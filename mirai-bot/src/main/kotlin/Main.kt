import com.google.gson.Gson
import com.google.gson.JsonObject
import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.MessageSource.Key.recall
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.*
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*

val admins = listOf//todo
val groups = listOf//todo
val banned = mutableListOf<String>()

fun newBot(account: Long, password: String): Bot {
    return BotFactory.newBot(account, password) {
        cacheDir = File("cache/${account}")
        protocol = BotConfiguration.MiraiProtocol.IPAD
        botLoggerSupplier = {
            val logger1 = MiraiLogger.Factory.create(Bot::class, "Bot ${it.id}")
            val logger2 = DirectoryLogger("Bot ${it.id}", File("logs/${it.id}"))
            object : MiraiLogger {
                override val identity: String? = logger1.identity
                override val isEnabled: Boolean = true

                override fun debug(message: String?) {
                    logger1.debug(message)
                    logger2.debug(message)
                }

                override fun debug(message: String?, e: Throwable?) {
                    logger1.debug(message, e)
                    logger2.debug(message, e)
                }

                override fun error(message: String?) {
                    logger1.error(message)
                    logger2.error(message)
                }

                override fun error(message: String?, e: Throwable?) {
                    logger1.error(message, e)
                    logger2.error(message, e)
                }

                override fun info(message: String?) {
                    logger1.info(message)
                    logger2.info(message)
                }

                override fun info(message: String?, e: Throwable?) {
                    logger1.info(message, e)
                    logger2.info(message, e)
                }

                override fun verbose(message: String?) {
                    logger1.verbose(message)
                    logger2.verbose(message)
                }

                override fun verbose(message: String?, e: Throwable?) {
                    logger1.verbose(message, e)
                    logger2.verbose(message, e)
                }

                override fun warning(message: String?) {
                    logger1.warning(message)
                    logger2.warning(message)
                }

                override fun warning(message: String?, e: Throwable?) {
                    logger1.warning(message, e)
                    logger2.warning(message, e)
                }
            }
        }
    }
}

val GSON = Gson()

fun sync() {
    try {
        val syncedCommit = File("synced-commit.txt").takeIf { it.exists() }?.readText()?.trim() ?: "<none>"
        val newHttpClient = HttpClient.newHttpClient()
        var res = newHttpClient.sendAsync(
            HttpRequest.newBuilder(URI("https://api.github.com/repos/zly2006/banned-words-chinese/branches/main"))
                .GET().build(), HttpResponse.BodyHandlers.ofString()
        ).get().body()
        val treeUrl = GSON.fromJson(res, JsonObject::class.java)["commit"].asJsonObject["commit"]
            .asJsonObject["tree"].asJsonObject["url"].asString
        res = newHttpClient.sendAsync(
            HttpRequest.newBuilder(URI(treeUrl))
                .GET().build(), HttpResponse.BodyHandlers.ofString()
        ).get().body()
        val file = GSON.fromJson(res, JsonObject::class.java)["tree"].asJsonArray.first {
            it.asJsonObject["path"].asString == "banned-words.txt"
        }.asJsonObject
        val commit = file["sha"].asString
        if (syncedCommit == commit) {
            println("Banned words already synced. ($commit)")
            return
        }
        res = newHttpClient.sendAsync(
            HttpRequest.newBuilder(URI(file["url"].asString))
                .GET().build(), HttpResponse.BodyHandlers.ofString()
        ).get().body()
        res = GSON.fromJson(res, JsonObject::class.java)["content"].asString
        println("Merging banned words...")
        val before = File("banned-words.txt").takeIf { it.exists() }?.readLines() ?: listOf()
        val after = res.lines().joinToString("") { it.decodeBase64().toString(Charsets.UTF_8) }.lines()
        val merged = (before + after).distinct()
        File("banned-words.txt").writeText(merged.joinToString("\n"))
        File("synced-commit.txt").writeText(commit)
        println("Banned words synced. ($syncedCommit -> $commit)")
    } catch (e: Exception) {
        println("Failed to get banned words. \n$e")
    }
}

suspend fun main(args: Array<String>) {
    sync()
    File("banned-words.txt").readLines().forEach {
        banned.add(Base64.getDecoder().decode(it).toString(Charsets.UTF_8))
    }
    val bot = newBot //todo
    bot.login()
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (banned.any { it in message.content }) {
            if (sender.id in admins) {
                return@subscribeAlways
            }
            if (group.id in groups) {
                message.recall()
                group.sendMessage("你说的话包含了不当言论，已被撤回。")
            }
        }
    }
}
