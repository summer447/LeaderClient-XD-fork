package unfair.ui.clickgui.oldgui

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import unfair.Unfair
import unfair.module.Category
import unfair.module.ModuleManager
import unfair.module.modules.render.GuiModule
import unfair.module.modules.render.HUD
import unfair.ui.clickgui.oldgui.components.BindComponent
import unfair.ui.clickgui.oldgui.components.CategoryComponent
import unfair.ui.clickgui.oldgui.components.ConfigCategoryComponent
import unfair.ui.clickgui.oldgui.components.ModuleComponent
import unfair.util.Timer
import unfair.util.shader.BlurUtils
import unfair.util.shader.RoundedUtils
import java.awt.Color
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.*
import kotlin.math.pow

class ClickGui : GuiScreen() {
    private val configFile = File("./config/Unfair/", "clickgui.txt")
    var originalScale: Int = 0
    var previousScale: Int = 0
    var updates: Float = 0f
    var last: Long = 0
    private var logoSmoothWidth: Timer? = null
    private var logoSmoothLength: Timer? = null
    private var smoothEntity: Timer? = null
    private var backgroundFade: Timer? = null
    private var blurSmooth: Timer? = null
    private val clientName = "Leader-XD"
    private var clickGuiOpen = false
    private var openedTime: Long = 0
    private var hudColorCached = Color.white.rgb
    private var lastHudColorUpdate: Long = 0
    private var cached = 0f
    @JvmField
    var isInitMain: Boolean = false

    init {
        instance = this
        categories = ArrayList<CategoryComponent>()
        var y = 5
        val values = Category.entries.toTypedArray()

        for (i in values.indices) {
            val c = values[i]
            val categoryComponent = if (c == Category.CONFIG) {
                ConfigCategoryComponent(c.displayName!!)
            } else {
                CategoryComponent(
                    c.displayName!!,
                    Objects.requireNonNull<ModuleManager>(Unfair.moduleManager).getModulesByCategory(c)
                )
            }
            categoryComponent.setY(y, false)
            categories.add(categoryComponent)
            y += 20
        }

        loadPositions()
    }

    fun initMain() {
        this.isInitMain = true
        (Timer(500.0f).also { this.backgroundFade = it }.also { this.blurSmooth = it }.also { this.smoothEntity = it }
            .also { this.logoSmoothWidth = it }).start()
    }

    override fun initGui() {
        super.initGui()
        if (!isNotFirstOpen) {
            isNotFirstOpen = true
            this.previousScale = 2
        }
        val sr = ScaledResolution(this.mc)
        for (categoryComponent in categories) {
            categoryComponent.setScreenHeight(sr.scaledHeight)
        }
        this.previousScale = 2
    }

    override fun drawScreen(x: Int, y: Int, p: Float) {
        val guiModule =
            Objects.requireNonNull<ModuleManager>(Unfair.moduleManager).modules[GuiModule::class.java] as GuiModule
        if (guiModule.blur.value) {
            BlurUtils.prepareBlur()
            RoundedUtils.drawRound(0f, 0f, this.width.toFloat(), this.height.toFloat(), 0.0f, true, Color.black)
            val inputToRange = 1.5f
            BlurUtils.blurEnd(2, this.blurSmooth!!.getValueFloat(0f, inputToRange, 1))
        }

        drawRect(
            0,
            0,
            this.width,
            this.height,
            (this.backgroundFade!!.getValueFloat(0.0f, 0.7f, 2) * 255.0f).toInt() shl 24
        )

        if (System.currentTimeMillis() - lastHudColorUpdate > 50) {
            val hud = Unfair.moduleManager!!.modules[HUD::class.java] as HUD
            hudColorCached = hud.getColor(System.currentTimeMillis()).rgb
            lastHudColorUpdate = System.currentTimeMillis()
        }

        for (c in categories) {
            c.render(this.fontRendererObj)
            c.mousePosition(x, y)

            for (m in c.modules) {
                m.drawScreen(x, y)
            }
        }

        GL11.glColor3f(1.0f, 1.0f, 1.0f)
        GlStateManager.pushMatrix()
        GlStateManager.disableBlend()
        if (this.mc.thePlayer != null) {
            GuiInventory.drawEntityOnScreen(
                this.width + 15 - this.smoothEntity!!.getValueInt(0, 40, 2),
                this.height - 10,
                40,
                (this.width - 25 - x).toFloat(),
                (this.height - 50 - y).toFloat(),
                this.mc.thePlayer
            )
        }
        GlStateManager.enableBlend()
        GlStateManager.popMatrix()
        onRenderTick(p)
    }

    private fun onRenderTick(partialTicks: Float) {
        if (!clickGuiOpen && this.mc.currentScreen is ClickGui) {
            clickGuiOpen = true
            initTimer(500.0f)
            startTimer()
            openedTime = System.currentTimeMillis()
        } else if (this.mc.currentScreen !is ClickGui) {
            clickGuiOpen = false
        } else {
            val displaySize = intArrayOf(this.width, this.height)
            var y = displaySize[1] + (8 - getValueInt(0, 30, 2))

            //this.fontRendererObj.drawString("$clientName-$clientVersion", 4f, y.toFloat(), hudColorCached, true)

            val elapsedTime = System.currentTimeMillis() - openedTime + 50L
            val characterIndex = (elapsedTime / 200L).toInt()
            y += this.fontRendererObj.FONT_HEIGHT + 1

            if (characterIndex < clientName.length) {
                var obfuscated = ""

                for (i in 0..<clientName.length) {
                    val currentChar = if (i < characterIndex)
                        clientName[i]
                    else ((Random()).nextInt(26) + 'a'.code).toChar()
                    obfuscated += currentChar
                }

                this.fontRendererObj.drawString(obfuscated, 4f, y.toFloat(), hudColorCached, true)
            } else {
                this.fontRendererObj.drawString(clientName, 4f, y.toFloat(), hudColorCached, true)
            }
        }
    }

    fun initTimer(updates: Float) {
        this.updates = updates
    }

    fun startTimer() {
        this.cached = 0.0f
        this.last = System.currentTimeMillis()
    }

    fun getValueFloat(begin: Float, end: Float, type: Int): Float {
        if (this.cached == end) {
            return this.cached
        } else {
            var t = (System.currentTimeMillis() - this.last).toFloat() / this.updates
            when (type) {
                1 -> t = if (t < 0.5f) 4.0f * t * t * t else (t - 1.0f) * (2.0f * t - 2.0f) * (2.0f * t - 2.0f) + 1.0f
                2 -> t = (1.0 - (1.0f - t).toDouble().pow(5.0)).toFloat()
            }

            var value = begin + t * (end - begin)
            if ((end > begin && value > end) || (end < begin && value < end)) {
                value = end
            }

            if (value == end) {
                this.cached = value
            }

            return value
        }
    }

    fun getValueInt(begin: Int, end: Int, type: Int): Int {
        return Math.round(this.getValueFloat(begin.toFloat(), end.toFloat(), type))
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton == 0) {
            var draggingAssigned = false
            for (i in categories.indices.reversed()) {
                val category: CategoryComponent = categories[i]
                if (!draggingAssigned && category.draggable(mouseX, mouseY)) {
                    category.overTitle(true)
                    category.xx = mouseX - category.getX()
                    category.yy = mouseY - category.getY()
                    category.dragging = true
                    draggingAssigned = true
                } else {
                    category.overTitle(false)
                }
            }
        }

        if (mouseButton == 1) {
            var toggled = false
            for (i in categories.indices.reversed()) {
                val category: CategoryComponent = categories[i]
                if (!toggled && category.overTitle(mouseX, mouseY)) {
                    category.mouseClicked(!category.isOpened)
                    toggled = true
                }
            }
        }

        for (category in categories) {
            if (category.isOpened && !category.modules.isEmpty() && category.overRect(mouseX, mouseY)) {
                for (component in category.modules) {
                    if (component is ModuleComponent) {
                        component.onClick(mouseX, mouseY, mouseButton)
                        category.openModule(component)
                    }
                }
            }
            if (category is ConfigCategoryComponent && category.isOpened) {
                category.mouseClicked(mouseX, mouseY, mouseButton)
            }
        }
    }

    public override fun mouseReleased(x: Int, y: Int, button: Int) {
        if (button == 0) {
            val iterator: MutableIterator<CategoryComponent> = categories.iterator()
            while (iterator.hasNext()) {
                val category = iterator.next()
                category.overTitle(false)
                if (category.isOpened && !category.modules.isEmpty()) {
                    for (module in category.modules) {
                        module.mouseReleased(x, y, button)
                    }
                }
            }
        }
    }

    override fun handleMouseInput() {
        try {
            super.handleMouseInput()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val wheelInput = Mouse.getDWheel()
        if (wheelInput != 0) {
            for (category in categories) {
                category.onScroll(wheelInput)
            }
        }
    }

    override fun setWorldAndResolution(
        p_setWorldAndResolution_1_: Minecraft,
        p_setWorldAndResolution_2_: Int,
        p_setWorldAndResolution_3_: Int
    ) {
        this.mc = p_setWorldAndResolution_1_
        originalScale = this.mc.gameSettings.guiScale
        this.mc.gameSettings.guiScale = 2
        this.itemRender = p_setWorldAndResolution_1_.renderItem
        this.fontRendererObj = p_setWorldAndResolution_1_.fontRendererObj
        val scaledresolution = ScaledResolution(this.mc)
        this.width = scaledresolution.scaledWidth
        this.height = scaledresolution.scaledHeight
        this.buttonList.clear()
        this.initGui()
    }

    public override fun keyTyped(t: Char, k: Int) {
        if (k == Keyboard.KEY_ESCAPE && !binding()) {
            this.mc.displayGuiScreen(null)
        } else {
            val iterator: MutableIterator<CategoryComponent> = categories.iterator()
            while (iterator.hasNext()) {
                val category = iterator.next()

                if (category.isOpened && !category.modules.isEmpty()) {
                    for (module in category.modules) {
                        module.keyTyped(t, k)
                    }
                }
            }
        }
    }

    override fun onGuiClosed() {
        this.logoSmoothLength = null
        for (c in categories) {
            c.dragging = false
            for (m in c.modules) {
                m.onGuiClosed()
            }
        }
        this.mc.gameSettings.guiScale = originalScale
        savePositions()
    }

    override fun doesGuiPauseGame(): Boolean {
        return false
    }

    private fun binding(): Boolean {
        for (c in categories) {
            for (component in c.modules) {
                if (component is ModuleComponent) {
                    for (setting in component.settings) {
                        if (setting is BindComponent && setting.isBinding) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun savePositions() {
        val json = JsonObject()
        for (cat in categories) {
            val pos = JsonObject()
            pos.addProperty("x", cat.getX())
            pos.addProperty("y", cat.getY())
            pos.addProperty("open", cat.isOpened)
            json.add(cat.name, pos)
        }
        val gson = GsonBuilder().setPrettyPrinting().create()
        try {
            FileWriter(configFile).use { writer ->
                gson.toJson(json, writer)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadPositions() {
        if (!configFile.exists()) return
        val parser = JsonParser()
        try {
            FileReader(configFile).use { reader ->
                val json = parser.parse(reader).getAsJsonObject()
                for (cat in categories) {
                    if (json.has(cat.name)) {
                        val pos = json.getAsJsonObject(cat.name)
                        cat.setX(pos.get("x").asInt)
                        cat.setY(pos.get("y").asInt)
                        cat.setOpened(pos.get("open").asBoolean)
                    }
                }
            }
        } catch (_: IOException) {}
    }

    companion object {
        lateinit var categories: ArrayList<CategoryComponent>
        @JvmField
        var instance: ClickGui? = null
        private var isNotFirstOpen = false
    }
}