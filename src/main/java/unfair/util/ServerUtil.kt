package unfair.util

import net.minecraft.client.Minecraft
import net.minecraft.scoreboard.Score
import net.minecraft.scoreboard.ScorePlayerTeam
import java.util.stream.Collectors

object ServerUtil {
    private val mc: Minecraft = Minecraft.getMinecraft()

    val scoreboardLines: ArrayList<String>
        get() {
            if (mc.theWorld == null) {
                return java.util.ArrayList<String>()
            }
            val scoreboard = mc.theWorld.scoreboard
            if (scoreboard == null) {
                return java.util.ArrayList<String>()
            }
            val scoreObjective = scoreboard.getObjectiveInDisplaySlot(1)
            if (scoreObjective == null) {
                return java.util.ArrayList<String>()
            }
            return scoreboard.getSortedScores(scoreObjective).stream().map<String?> { score: Score? ->
                ScorePlayerTeam.formatPlayerName(
                    scoreboard.getPlayersTeam(score!!.getPlayerName()),
                    score.playerName
                )
            }.collect(Collectors.toList()) as java.util.ArrayList<String>
        }

    val isHypixel: Boolean
        get() {
            val arrayList: java.util.ArrayList<String> = scoreboardLines
            if (arrayList.isEmpty()) return false
            if (arrayList.get(0) == "§ewww.hypixel.ne🎂§et") return true
            return arrayList.get(0) == "§ewww.hypixel.ne§g§et"
        }

    @JvmStatic
    fun hasPlayerCountInfo(): Boolean {
        for (s in scoreboardLines) {
            if (!s.matches(".*Players: §a\\d+/\\d+.*".toRegex())) continue
            return true
        }
        return false
    }
}
