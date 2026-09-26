@file:Suppress("DuplicatedCode")

package unfair.ui.clickgui.panel

import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.EntityLivingBase
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import unfair.Unfair
import unfair.event.EventManager
import unfair.event.EventTarget
import unfair.events.Render2DEvent
import unfair.management.ClientSettings
import unfair.module.Category
import unfair.module.Module
import unfair.property.Property
import unfair.property.properties.*
import unfair.ui.clickgui.panel.config.ConfigEntry
import unfair.ui.clickgui.panel.config.CreateConfigEntry
import unfair.ui.clickgui.panel.config.SettingsEntry
import unfair.ui.clickgui.panel.settings.BoolSetting
import unfair.ui.clickgui.panel.settings.ColorSetting
import unfair.ui.clickgui.panel.settings.ModeSetting
import unfair.ui.clickgui.panel.settings.RangeSliderSetting
import unfair.ui.clickgui.panel.settings.SliderSetting
import unfair.util.RenderUtil
import unfair.util.shader.BlurUtils.blurEnd
import unfair.util.shader.BlurUtils.prepareBlur
import unfair.util.shader.RoundedUtils.drawRound
import java.awt.Color
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class PanelGui : GuiScreen() {
    private var displayHealth = 0f
    private var healthInitialized = false

    private var selectedCategoryIndex = 0
    private val categoryList: MutableList<Category> = ArrayList<Category>()
    private val moduleEntries: MutableList<ModuleEntry> = ArrayList<ModuleEntry>()
    private val filteredEntries: MutableList<ModuleEntry> = ArrayList<ModuleEntry>()
    private val configEntries: MutableList<ConfigEntry> = ArrayList<ConfigEntry>()
    private val filteredConfigs: MutableList<ConfigEntry> = ArrayList<ConfigEntry>()
    private val settingsEntries: MutableList<SettingsEntry> = ArrayList<SettingsEntry>()
    private var createConfigEntry: CreateConfigEntry? = null
    private var configListDirty = true

    private val allModuleEntries: MutableList<ModuleEntry> = ArrayList<ModuleEntry>()
    private var preSearchCategoryIndex = 0
    private var categoryTitleAlpha = 1f

    private var scrollY = 0f
    private var targetScrollY = 0f
    private var maxScrollY = 0f

    private var lastRenderTime: Long = 0
    private var catSelectAnimY = 0f
    private var catSelectTargetY = 0f
    private var contentAlpha = 1f
    private var categoryChanging = false
    private var categoryFadingIn = false
    private var categoryChangeTimer = 0f
    private var prevSelectedCategoryIndex = 0

    private var searchText = ""
    private var searchFocused = false
    private var searchCursorPos = 0
    private var searchSelectionStart = -1
    private var searchAlpha = 0f
    private var searchWidthAnim = 0f
    private var searchActive = false
    private var filterAlpha = 1f

    private var backspaceHeld = false
    private var backspaceRepeatTimer = 0f
    private var textAnimProgress = 1f
    private var lastTextLength = 0

    private var guiAlpha = 0f
    private var closing = false
    private var renderDt = 0.016f
    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (mc.ingameGUI != null && mc.ingameGUI.chatGUI != null) {
            mc.ingameGUI.chatGUI.clearChatMessages()
        }
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    init {
        EventManager.register(this)
        Collections.addAll(categoryList, *Category.entries.toTypedArray())
    }

    override fun initGui() {
        rebuildAllModules()
        rebuildModules()
        refreshConfigs()
        refreshSettings()
        createConfigEntry = CreateConfigEntry()
        lastRenderTime = System.currentTimeMillis()
        guiAlpha = 0f
        closing = false
    }

    @EventTarget
    fun onRender2D(event: Render2DEvent?) {
        val now = System.currentTimeMillis()
        val dt = min((now - lastRenderTime) / 1000f, 0.1f)
        lastRenderTime = now
        renderDt = dt

        updateAnimations(dt)

        val scale = ClientSettings.INSTANCE.guiScale
        val scaledWidth: Float = PANEL_WIDTH * scale
        val scaledHeight: Float = PANEL_HEIGHT * scale

        val x = (this.width - scaledWidth) / 2f
        val y = (this.height - scaledHeight) / 2f

        val useBlur = ClientSettings.INSTANCE.isBlurArea

        GlStateManager.enableBlend()

        val bgLayerAlpha = ClientSettings.INSTANCE.getLayeredAlpha(guiAlpha, 0)

        if (useBlur) {
            prepareBlur()
            val bg = ClientSettings.INSTANCE.backgroundColor
            drawRound(
                x, y, scaledWidth, scaledHeight, RADIUS * scale,
                Color(
                    bg.red, bg.green, bg.blue,
                    (guiAlpha * 255).toInt()
                ).rgb
            )
            blurEnd(2, 8f)
        }

        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, 0f)
        GlStateManager.scale(scale, scale, 1f)
        GlStateManager.translate(-x, -y, 0f)

        GlStateManager.color(1f, 1f, 1f, bgLayerAlpha)

        val bgColorRaw = ClientSettings.INSTANCE.backgroundColor
        val bgWithAlpha: Color = blendAlpha(bgColorRaw, bgLayerAlpha)
        drawRound(x, y, PANEL_WIDTH, PANEL_HEIGHT, RADIUS, bgWithAlpha)

        drawLogo(x, y)
        drawCategories(x, y)
        drawPlayerInfo(x, y)

        val contentX: Float = x + SIDEBAR_WIDTH + 8
        val contentTop = y + 10

        drawSearchBar(contentX, contentTop)
        drawCategoryTitle(contentX, contentTop)
        drawModuleArea(x, y, contentX, contentTop)

        GL11.glDisable(GL11.GL_SCISSOR_TEST)

        GlStateManager.popMatrix()

        GlStateManager.disableBlend()
    }

    private fun updateAnimations(dt: Float) {
        ClientSettings.INSTANCE.update(dt)

        if (closing) {
            guiAlpha = PanelValueItem.lerp(guiAlpha, 0f, GUI_ANIM_SPEED, dt)
            if (guiAlpha < 0.01f) {
                mc.displayGuiScreen(null)
                return
            }
        } else {
            guiAlpha = PanelValueItem.lerp(guiAlpha, 1f, GUI_ANIM_SPEED, dt)
        }

        scrollY = PanelValueItem.lerp(scrollY, targetScrollY, 0.14f, dt)
        if (abs(scrollY - targetScrollY) < 0.5f) scrollY = targetScrollY

        catSelectAnimY = PanelValueItem.lerp(catSelectAnimY, catSelectTargetY, 0.18f, dt)

        if (categoryChanging) {
            categoryChangeTimer += dt
            contentAlpha = PanelValueItem.lerp(contentAlpha, 0f, 0.14f, dt)
            if (contentAlpha < 0.03f && categoryChangeTimer > 0.15f) {
                rebuildModules()
                if (categoryList[selectedCategoryIndex] == Category.CONFIG || categoryList[selectedCategoryIndex] == Category.SETTINGS) {
                    resetConfigAnimations()
                }
                categoryChanging = false
                categoryFadingIn = true
                categoryChangeTimer = 0f
                contentAlpha = 0f
            }
        } else if (categoryFadingIn) {
            contentAlpha = PanelValueItem.lerp(contentAlpha, 1f, 0.11f, dt)
            if (contentAlpha > 0.98f) {
                contentAlpha = 1f
                categoryFadingIn = false
            }
        } else {
            contentAlpha = PanelValueItem.lerp(contentAlpha, 1f, 0.11f, dt)
        }

        contentAlpha *= guiAlpha

        if (searchFocused || searchActive) {
            searchAlpha = PanelValueItem.lerp(searchAlpha, 1f, 0.15f, dt)
            searchWidthAnim = PanelValueItem.lerp(searchWidthAnim, 1f, 0.15f, dt)
            if (!searchText.isEmpty() && categoryList[selectedCategoryIndex] != Category.CONFIG && categoryList[selectedCategoryIndex] != Category.SETTINGS
            ) {
                categoryTitleAlpha = PanelValueItem.lerp(categoryTitleAlpha, 0f, 0.15f, dt)
            } else {
                categoryTitleAlpha = PanelValueItem.lerp(categoryTitleAlpha, 1f, 0.15f, dt)
            }
        } else {
            searchAlpha = PanelValueItem.lerp(searchAlpha, 0f, 0.15f, dt)
            searchWidthAnim = PanelValueItem.lerp(searchWidthAnim, 0f, 0.15f, dt)
            categoryTitleAlpha = PanelValueItem.lerp(categoryTitleAlpha, 1f, 0.15f, dt)
        }

        searchAlpha *= guiAlpha

        filterAlpha = PanelValueItem.lerp(filterAlpha, (if (searchText.isEmpty()) 1f else 0.3f), 0.12f, dt)

        checkBackspaceReleased()

        if (backspaceHeld && searchFocused) {
            backspaceRepeatTimer += dt
            if (backspaceRepeatTimer >= BACKSPACE_INITIAL_DELAY) {
                val repeatTime: Float = backspaceRepeatTimer - BACKSPACE_INITIAL_DELAY
                val deleteCount = (repeatTime / BACKSPACE_REPEAT_RATE).toInt()
                if (deleteCount > 0) {
                    var i = 0
                    while (i < deleteCount && !searchText.isEmpty()) {
                        if (searchSelectionStart >= 0) {
                            val selMin = min(searchSelectionStart, searchCursorPos)
                            val selMax = max(searchSelectionStart, searchCursorPos)
                            searchText = searchText.substring(0, selMin) + searchText.substring(selMax)
                            searchCursorPos = selMin
                            searchSelectionStart = -1
                            applyFilter()
                        } else if (searchCursorPos > 0) {
                            searchText =
                                searchText.substring(0, searchCursorPos - 1) + searchText.substring(searchCursorPos)
                            searchCursorPos--
                            applyFilter()
                        }
                        i++
                    }
                    backspaceRepeatTimer = BACKSPACE_INITIAL_DELAY + (repeatTime % BACKSPACE_REPEAT_RATE)
                }
            }
        }

        if (searchText.length != lastTextLength) {
            textAnimProgress = 0f
            lastTextLength = searchText.length
        }
        textAnimProgress = PanelValueItem.lerp(textAnimProgress, 1f, 0.25f, dt)

        for (entry in moduleEntries) {
            entry.toggleAnim = PanelValueItem.lerp(entry.toggleAnim, if (entry.mod.isEnabled) 1f else 0f, 0.15f, dt)
            entry.hideAnim = PanelValueItem.lerp(entry.hideAnim, if (entry.mod.isHidden) 1f else 0f, 0.15f, dt)
            entry.actionButtons.update(dt)
            for (s in entry.settings) s.update(dt)
        }

        for (entry in allModuleEntries) {
            if (!moduleEntries.contains(entry)) {
                entry.toggleAnim =
                    PanelValueItem.lerp(entry.toggleAnim, if (entry.mod.isEnabled) 1f else 0f, 0.15f, dt)
                entry.hideAnim = PanelValueItem.lerp(entry.hideAnim, if (entry.mod.isHidden) 1f else 0f, 0.15f, dt)
                entry.actionButtons.update(dt)
                for (s in entry.settings) s.update(dt)
            }
        }
    }

    private fun drawLogo(x: Float, y: Float) {
        val logoSize = 36f
        val logoX = x + 16
        val logoY = y + 14
        drawRound(logoX, logoY, logoSize, logoSize, 7f, blendAlpha(ClientSettings.INSTANCE.accentColor, guiAlpha))

        val logoText = "L"
        val logoFontSize = 24
        val lw = Unfair.fontManager!!.getFont(logoFontSize).getStringWidth(logoText).toFloat()
        Unfair.fontManager!!.getFont(logoFontSize).drawString(
            logoText,
            logoX + (logoSize - lw) / 2f, logoY + (logoSize - logoFontSize) / 2f + 7,
            blendAlpha(ClientSettings.INSTANCE.logoTextColor, guiAlpha).rgb, false
        )

        val nameX = logoX + logoSize + 12
        Unfair.fontManager!!.getFont(22).drawString(
            "Leader",
            nameX,
            logoY + 2,
            blendAlpha(ClientSettings.INSTANCE.titleMainColor, guiAlpha).rgb,
            false
        )
        Unfair.fontManager!!.getFont(13).drawString(
            "version " + Unfair.version,
            nameX,
            logoY + 18,
            blendAlpha(ClientSettings.INSTANCE.titleSubColor, guiAlpha).rgb,
            false
        )
    }

    private fun drawCategories(x: Float, y: Float) {
        val headerY = y + 68
        val sidebarAlpha = ClientSettings.INSTANCE.getLayeredAlpha(guiAlpha, 1)

        Unfair.fontManager!!.getFont(20).drawString(
            "Modules", x + 16, headerY,
            blendAlpha(ClientSettings.INSTANCE.sectionHeaderColor, guiAlpha).rgb, false
        )

        var catY = headerY + 15

        for (category in categoryList) {
            if (category == Category.CONFIG || category == Category.SETTINGS) {
                break
            }
        }

        catSelectTargetY = catY
        for (i in 0..<selectedCategoryIndex) {
            val isConfig = categoryList[i] == Category.CONFIG
            if (isConfig) {
                catSelectTargetY += CONFIG_DOWN_OFFSET + CLIENT_TITLE_SPACING
            }
            catSelectTargetY += MODULE_CAT_H
        }
        if (categoryList[selectedCategoryIndex] == Category.CONFIG) {
            catSelectTargetY += CONFIG_DOWN_OFFSET + CLIENT_TITLE_SPACING
        }
        if (!java.lang.Float.isFinite(catSelectAnimY)) catSelectAnimY = catSelectTargetY

        val selBgW: Float = SIDEBAR_WIDTH - 20
        val selBgX = x + 10
        val selBgH: Float = MODULE_CAT_H - 4
        val selBgActualY: Float = catSelectAnimY + (MODULE_CAT_H - selBgH) / 2f - NONCONFIG_SEL_BG_UP
        drawRound(
            selBgX,
            selBgActualY,
            selBgW,
            selBgH,
            5f,
            blendAlpha(ClientSettings.INSTANCE.selCatBgColor, sidebarAlpha)
        )

        for (i in categoryList.indices) {
            val isConfig = categoryList[i] == Category.CONFIG
            val isClientCat = categoryList[i] == Category.CONFIG || categoryList[i] == Category.SETTINGS

            if (isConfig) {
                catY += CONFIG_DOWN_OFFSET
                Unfair.fontManager!!.getFont(20).drawString(
                    "Client", x + 16, catY,
                    blendAlpha(ClientSettings.INSTANCE.sectionHeaderColor, guiAlpha).rgb, false
                )
                catY += CLIENT_TITLE_SPACING
            }

            val catName = categoryList[i].displayName
            val textColor: Int = if (i == selectedCategoryIndex) blendAlpha(
                ClientSettings.INSTANCE.textColor,
                guiAlpha
            ).rgb else blendAlpha(ClientSettings.INSTANCE.categoryTextColor, guiAlpha).rgb

            val textOffsetY = if (!isClientCat) 3f else 4f
            Unfair.fontManager!!.getFont(16).drawString(catName, x + 16, catY + textOffsetY, textColor, false)
            catY += MODULE_CAT_H
        }
    }

    private fun drawSearchBar(contentX: Float, contentTop: Float) {
        val baseW = 150f
        val expandedW = 220f
        val currentW = baseW + (expandedW - baseW) * searchWidthAnim
        val searchH = 22f

        val inputAlpha = ClientSettings.INSTANCE.getLayeredAlpha(guiAlpha, 3)
        val bgAlpha = (0.55f + 0.45f * searchWidthAnim) * inputAlpha
        val bgColor: Color = blendAlpha(ClientSettings.INSTANCE.searchBgColor, bgAlpha)
        drawRound(contentX, contentTop, currentW, searchH, 4f, bgColor)

        //UnfairGaming 批注:这个傻逼矩形到底有何用?
//        if (searchAlpha > 0.05f && guiAlpha > 0.05f) {
//            Color borderColor = blendAlpha(ClientSettings.INSTANCE.getAccentColor(), searchAlpha * 0.6f * inputAlpha);
//            RenderUtil.drawRect(contentX, contentTop + searchH - 1, contentX + currentW, contentTop + searchH, borderColor.getRGB());
//        }
        if (searchText.isEmpty() && !searchFocused) {
            val placeholderAlpha = (0.6f + 0.4f * (1f - searchWidthAnim)) * guiAlpha
            Unfair.fontManager!!.getFont(20).drawString(
                "Search...", contentX + 10, contentTop + 6,
                blendAlpha(ClientSettings.INSTANCE.searchPlaceholderColor, placeholderAlpha).rgb, false
            )
        } else {
            if (searchSelectionStart >= 0 && searchSelectionStart != searchCursorPos) {
                val selMin = min(searchSelectionStart, searchCursorPos)
                val selMax = max(searchSelectionStart, searchCursorPos)
                val beforeSel = searchText.substring(0, selMin)
                val selX = contentX + 10 + Unfair.fontManager!!.getFont(20).getStringWidth(beforeSel)
                val selW =
                    Unfair.fontManager!!.getFont(20).getStringWidth(searchText.substring(selMin, selMax)).toFloat()
                RenderUtil.drawRect(
                    selX.toDouble(),
                    (contentTop + 3).toDouble(),
                    (selX + selW).toDouble(),
                    (contentTop + 18).toDouble(),
                    blendAlpha(ClientSettings.INSTANCE.accentColor, 0.3f * guiAlpha).rgb
                )
            }

            val displayTextAlpha = guiAlpha

            Unfair.fontManager!!.getFont(20).drawString(
                searchText, contentX + 10, contentTop + 6,
                blendAlpha(ClientSettings.INSTANCE.inputTextColor, searchAlpha * displayTextAlpha).rgb, false
            )

            if (searchFocused) {
                val beforeCursor = searchText.substring(0, searchCursorPos)
                val cursorX = contentX + 10 + Unfair.fontManager!!.getFont(20).getStringWidth(beforeCursor)

                val cursorTime = System.currentTimeMillis()
                val cursorVisible = (cursorTime % 1000 < 500)

                if (cursorVisible) {
                    val cursorAlpha = (sin(cursorTime / 200.0 * Math.PI) * 0.3f + 0.7f).toFloat()
                    RenderUtil.drawRect(
                        cursorX.toDouble(),
                        (contentTop + 3).toDouble(),
                        (cursorX + 1.0f).toDouble(),
                        (contentTop + 18).toDouble(),
                        blendAlpha(
                            ClientSettings.INSTANCE.accentColor,
                            searchAlpha * cursorAlpha * guiAlpha
                        ).rgb
                    )
                }
            }
        }
    }

    private fun drawCategoryTitle(contentX: Float, contentTop: Float) {
        val renderCatIdx = if (categoryChanging) prevSelectedCategoryIndex else selectedCategoryIndex
        val selectedCat = categoryList[renderCatIdx]

        if (selectedCat == Category.CONFIG || selectedCat == Category.SETTINGS) {
            val categoryName = selectedCat.displayName
            Unfair.fontManager!!.getFont(22).drawString(
                categoryName,
                contentX,
                contentTop + 38,
                blendAlpha(ClientSettings.INSTANCE.titleMainColor, contentAlpha * categoryTitleAlpha).rgb,
                false
            )
        } else {
            if (searchActive && !searchText.isEmpty()) {
                val searchResultText = "Search Results"
                Unfair.fontManager!!.getFont(22).drawString(
                    searchResultText, contentX, contentTop + 38,
                    blendAlpha(ClientSettings.INSTANCE.titleMainColor, contentAlpha).rgb, false
                )
            } else {
                val categoryName = selectedCat.displayName
                Unfair.fontManager!!.getFont(22).drawString(
                    categoryName,
                    contentX,
                    contentTop + 38,
                    blendAlpha(ClientSettings.INSTANCE.titleMainColor, contentAlpha * categoryTitleAlpha).rgb,
                    false
                )
            }
        }
    }

    private fun drawModuleArea(panelX: Float, panelY: Float, contentX: Float, contentTop: Float) {
        val categoryTitleH = 24f
        val moduleAreaTop = contentTop + 38 + categoryTitleH
        val moduleAreaBottom: Float = panelY + PANEL_HEIGHT - 12
        val moduleAreaHeight = moduleAreaBottom - moduleAreaTop
        val contentRight: Float = panelX + PANEL_WIDTH - 8
        val contentWidth = contentRight - contentX

        val renderCategoryIndex = if (categoryChanging) prevSelectedCategoryIndex else selectedCategoryIndex

        if (categoryList[renderCategoryIndex] == Category.CONFIG) {
            drawConfigArea(contentX, moduleAreaTop, contentWidth, moduleAreaHeight)
            return
        }

        if (categoryList[renderCategoryIndex] == Category.SETTINGS) {
            drawSettingsArea(contentX, moduleAreaTop, contentWidth, moduleAreaHeight)
            return
        }

        val colWidth: Float = (contentWidth - COL_GAP) / 2f
        val col2X: Float = contentX + colWidth + COL_GAP

        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        applyScissor(contentX, moduleAreaTop, contentWidth, moduleAreaHeight)

        val visible = if (searchActive && !searchText.isEmpty()) filteredEntries else moduleEntries

        var leftY = moduleAreaTop - scrollY
        var rightY = moduleAreaTop - scrollY

        for (entry in visible) {
            val ex = if (entry.col == 0) contentX else col2X
            val ey = if (entry.col == 0) leftY else rightY
            entry.x = ex
            entry.width = colWidth

            val cardH = entry.totalHeight

            val cardAlpha = ClientSettings.INSTANCE.getLayeredAlpha(contentAlpha, 2)
            val baseEnabledColor: Color = blendColor(
                ClientSettings.INSTANCE.cardEnabledColor,
                ClientSettings.INSTANCE.cardHiddenEnabledColor,
                entry.hideAnim
            )
            val cardColor: Color = blendColor(
                blendAlpha(ClientSettings.INSTANCE.cardColor, cardAlpha),
                blendAlpha(baseEnabledColor, cardAlpha),
                entry.toggleAnim
            )
            drawRound(ex, ey, colWidth, cardH, 5f, cardColor)

            GlStateManager.color(1f, 1f, 1f, cardAlpha)

            val mod = entry.mod
            val nameColor = blendAlpha(
                if (mod.isEnabled) ClientSettings.INSTANCE.textColor else ClientSettings.INSTANCE.categoryTextColor,
                contentAlpha
            ).rgb.toFloat()
            Unfair.fontManager!!.getFont(17).drawString(mod.getName(), ex + 12, ey + 5, nameColor.toInt(), false)

            val actionBtnWidth = ((24 + 2) * 2 - 2).toFloat()
            val actionBtnX = ex + colWidth - actionBtnWidth - 44
            val actionBtnY = ey + 3

            val rawMX = (Mouse.getX() * this.width / this.mc.displayWidth.toDouble()).toInt()
            val rawMY = (this.height - Mouse.getY() * this.height / this.mc.displayHeight.toDouble() - 1).toInt()
            val transformed = transformMouseCoords(rawMX, rawMY)
            val mx = transformed[0]
            val mY = transformed[1]

            entry.actionButtons.x = actionBtnX
            entry.actionButtons.y = actionBtnY
            entry.actionButtons.width = actionBtnWidth
            entry.actionButtons.alpha = cardAlpha
            entry.actionButtons.update(renderDt)
            entry.actionButtons.render(mx, mY)

            val toggleW = 30f
            val toggleH = 14f
            val toggleX = ex + colWidth - toggleW - 12
            val toggleY = ey + 4

            val trackColor: Color = blendColor(
                ClientSettings.INSTANCE.toggleOffColor,
                ClientSettings.INSTANCE.accentColor,
                entry.toggleAnim
            )
            drawRound(toggleX, toggleY, toggleW, toggleH, toggleH / 2f, blendAlpha(trackColor, cardAlpha))

            val dotSize = toggleH - 4
            val dotX = toggleX + 2 + entry.toggleAnim * (toggleW - dotSize - 4)
            val dotY = toggleY + 2
            val dotColor: Color = blendAlpha(ClientSettings.INSTANCE.cardColor, cardAlpha)
            drawRound(dotX, dotY, dotSize, dotSize, dotSize / 2f, dotColor)

            var settingY = ey + 24

            for (setting in entry.settings) {
                val settingVisAlpha = setting.visibilityAlpha
                if (settingVisAlpha < 0.001f) continue
                setting.x = ex + 12
                setting.y = settingY
                setting.width = colWidth - 24
                setting.alpha = cardAlpha

                if (setting is ModeSetting) {
                    setting.panelScreenX = panelX
                    setting.panelScreenY = panelY
                    val setH = setting.height
                    val modeHovering =
                        mx >= ex + 12 && mx <= ex + colWidth - 12 && mY >= settingY && mY <= settingY + setH
                    setting.setHovering(modeHovering)
                    setting.render(mx, mY)
                } else if (setting is ColorSetting) {
                    setting.render(mx, mY)
                } else {
                    setting.render(mx, mY)
                }
                settingY += setting.getHeight() * settingVisAlpha + 1
            }

            GlStateManager.color(1f, 1f, 1f, 1f)

            if (entry.col == 0) leftY += cardH + 4
            else rightY += cardH + 4
        }

        maxScrollY = calculateMaxScroll(moduleAreaHeight, visible)
        if (targetScrollY < 0) targetScrollY = 0f
        if (targetScrollY > maxScrollY) targetScrollY = maxScrollY
    }

    private fun drawConfigArea(contentX: Float, areaTop: Float, contentWidth: Float, areaHeight: Float) {
        if (createConfigEntry!!.justCreatedConfig()) {
            configListDirty = true
        }

        if (configListDirty) {
            refreshConfigs()
        }

        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        applyScissor(contentX, areaTop, contentWidth, areaHeight)

        val mx = (Mouse.getX() * this.width / this.mc.displayWidth.toDouble()).toInt()
        val mY = (this.height - Mouse.getY() * this.height / this.mc.displayHeight.toDouble() - 1).toInt()

        val transformed = transformMouseCoords(mx, mY)
        val logicMX = transformed[0]
        val logicMY = transformed[1]

        val visible = if (searchActive && !searchText.isEmpty()) filteredConfigs else configEntries

        var currentY = areaTop - scrollY
        val cardW: Float = (contentWidth - COL_GAP)
        val cardX: Float = contentX + COL_GAP / 2f
        val cardAlpha = ClientSettings.INSTANCE.getLayeredAlpha(contentAlpha, 2)

        createConfigEntry!!.x = cardX
        createConfigEntry!!.y = currentY
        createConfigEntry!!.width = cardW
        createConfigEntry!!.alpha = cardAlpha
        createConfigEntry!!.update(renderDt)
        createConfigEntry!!.render(logicMX, logicMY)
        currentY += createConfigEntry!!.height + 8

        for (entry in visible) {
            entry.x = cardX
            entry.y = currentY
            entry.width = cardW
            entry.alpha = cardAlpha
            entry.update(renderDt)
            entry.render(logicMX, logicMY)

            currentY += entry.height + 6
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST)

        var totalHeight = createConfigEntry!!.height + 8
        for (entry in visible) {
            totalHeight += entry.height + 6
        }

        maxScrollY = max(totalHeight - areaHeight, 0f)
        if (targetScrollY < 0) targetScrollY = 0f
        if (targetScrollY > maxScrollY) targetScrollY = maxScrollY
    }

    private fun drawSettingsArea(contentX: Float, areaTop: Float, contentWidth: Float, areaHeight: Float) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        applyScissor(contentX, areaTop, contentWidth, areaHeight)

        val mx = (Mouse.getX() * this.width / this.mc.displayWidth.toDouble()).toInt()
        val mY = (this.height - Mouse.getY() * this.height / this.mc.displayHeight.toDouble() - 1).toInt()

        val transformed = transformMouseCoords(mx, mY)
        val logicMX = transformed[0]
        val logicMY = transformed[1]

        val colWidth: Float = (contentWidth - COL_GAP) / 2f
        val col2X: Float = contentX + colWidth + COL_GAP
        val cardAlpha = ClientSettings.INSTANCE.getLayeredAlpha(contentAlpha, 2)

        var leftY = areaTop - scrollY
        var rightY = areaTop - scrollY

        for (i in settingsEntries.indices) {
            val entry = settingsEntries[i]
            val cardX = if (i % 2 == 0) contentX else col2X
            val cardY = if (i % 2 == 0) leftY else rightY

            entry.x = cardX
            entry.y = cardY
            entry.width = colWidth
            entry.alpha = cardAlpha
            entry.update(renderDt)
            entry.render(logicMX, logicMY)

            if (i % 2 == 0) {
                leftY += entry.height + 6
            } else {
                rightY += entry.height + 6
            }
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST)

        var leftTotalHeight = 0f
        var rightTotalHeight = 0f
        for (i in settingsEntries.indices) {
            if (i % 2 == 0) {
                leftTotalHeight += settingsEntries[i].height + 6
            } else {
                rightTotalHeight += settingsEntries[i].height + 6
            }
        }

        val totalHeight = max(leftTotalHeight, rightTotalHeight)
        maxScrollY = max(totalHeight - areaHeight, 0f)
        if (targetScrollY < 0) targetScrollY = 0f
        if (targetScrollY > maxScrollY) targetScrollY = maxScrollY
    }

    private fun drawPlayerInfo(panelX: Float, panelY: Float) {
        val moduleAreaBottom: Float = panelY + PANEL_HEIGHT - 8
        val infoX = panelX + 10
        val infoY = moduleAreaBottom - 48
        val infoW: Float = SIDEBAR_WIDTH - 20
        val infoH = 44f

        val sidebarAlpha = ClientSettings.INSTANCE.getLayeredAlpha(guiAlpha, 1)

        drawRound(infoX, infoY, infoW, infoH, 5f, blendAlpha(ClientSettings.INSTANCE.sidebarColor, sidebarAlpha))

        val player: EntityLivingBase? = mc.thePlayer
        if (player == null) return

        val playerName = player.name
        val nameX = infoX + 10
        val textY = infoY + 8
        Unfair.fontManager!!.getFont(15).drawString(
            playerName,
            nameX,
            textY,
            blendAlpha(ClientSettings.INSTANCE.textColor, guiAlpha).rgb,
            false
        )

        val health = player.health
        val maxHealth = player.maxHealth

        if (!healthInitialized) {
            displayHealth = health
            healthInitialized = true
        }
        displayHealth = PanelValueItem.lerp(displayHealth, health, 0.12f, renderDt)

        val hpBarY = textY + 20
        val hpBarW = infoW - 20
        val hpBarH = 4f
        RenderUtil.drawRect(
            nameX.toDouble(),
            hpBarY.toDouble(),
            (nameX + hpBarW).toDouble(),
            (hpBarY + hpBarH).toDouble(),
            blendAlpha(ClientSettings.INSTANCE.hpBarTrackColor, sidebarAlpha).rgb
        )
        if (maxHealth > 0 && displayHealth > 0) {
            val fillW = hpBarW * min(displayHealth / maxHealth, 1f)
            if (fillW > 0) {
                drawRound(
                    nameX,
                    hpBarY,
                    fillW,
                    hpBarH,
                    1.5f,
                    blendAlpha(ClientSettings.INSTANCE.accentColor, sidebarAlpha)
                )
            }
        }
    }

    @Throws(IOException::class)
    protected override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        var mouseX = mouseX
        var mouseY = mouseY
        if (closing) return

        val transformed = transformMouseCoords(mouseX, mouseY)
        mouseX = transformed[0]
        mouseY = transformed[1]

        val scale = ClientSettings.INSTANCE.guiScale
        val scaledWidth: Float = PANEL_WIDTH * scale
        val scaledHeight: Float = PANEL_HEIGHT * scale
        val x = (this.width - scaledWidth) / 2f
        val y = (this.height - scaledHeight) / 2f

        val contentX: Float = x + SIDEBAR_WIDTH + 8
        val contentTop = y + 10
        val baseW = 150f
        val expandedW = 220f
        val searchW = baseW + (expandedW - baseW) * searchWidthAnim
        val searchH = 22f

        if (mouseX >= contentX && mouseX <= contentX + searchW && mouseY >= contentTop && mouseY <= contentTop + searchH) {
            if (!searchActive) {
                preSearchCategoryIndex = selectedCategoryIndex
            }
            searchFocused = true
            searchActive = true
            return
        }

        val headerY = y + 72

        for (category in categoryList) {
            if (category == Category.CONFIG || category == Category.SETTINGS) {
                break
            }
        }

        var catClickY = headerY + 7
        for (i in categoryList.indices) {
            val isConfig = categoryList[i] == Category.CONFIG
            val isClientCat = categoryList[i] == Category.CONFIG || categoryList[i] == Category.SETTINGS
            if (isConfig) {
                catClickY += CONFIG_DOWN_OFFSET + CLIENT_TITLE_SPACING
            }
            val itemH: Float = MODULE_CAT_H

            if (mouseX >= x + 10 && mouseX <= x + SIDEBAR_WIDTH - 10 && mouseY >= catClickY && mouseY <= catClickY + itemH) {
                if (searchActive || searchFocused) {
                    searchFocused = false
                    searchActive = false
                    searchText = ""
                    searchCursorPos = 0
                    searchSelectionStart = -1
                }
                if (i != selectedCategoryIndex) {
                    closeAllColorPickers()
                    prevSelectedCategoryIndex = selectedCategoryIndex
                    selectedCategoryIndex = i
                    categoryChanging = true
                    categoryFadingIn = false
                    categoryChangeTimer = 0f
                }
                return
            }

            catClickY += itemH
        }

        val moduleAreaTop = contentTop + 62
        val contentRight: Float = x + PANEL_WIDTH - 8
        val contentWidth = contentRight - contentX

        if (categoryList[selectedCategoryIndex] == Category.CONFIG) {
            handleConfigClicks(mouseX, mouseY, mouseButton)
        } else if (categoryList[selectedCategoryIndex] == Category.SETTINGS) {
            handleSettingsClicks(mouseX, mouseY, mouseButton)
        } else {
            val colWidth: Float = (contentWidth - COL_GAP) / 2f
            val col2X: Float = contentX + colWidth + COL_GAP

            val visible = if (searchActive && !searchText.isEmpty()) filteredEntries else moduleEntries
            var leftClickY = moduleAreaTop - scrollY
            var rightClickY = moduleAreaTop - scrollY
            for (entry in visible) {
                val ex = if (entry.col == 0) contentX else col2X
                val clickY = if (entry.col == 0) leftClickY else rightClickY
                val cardH = entry.totalHeight
                if (mouseX >= ex && mouseX <= ex + colWidth && mouseY >= clickY && mouseY <= clickY + cardH) {
                    if (mouseButton == 0) {
                        val actionBtnWidth = ((24 + 2) * 2 - 2).toFloat()
                        val actionBtnX = ex + colWidth - actionBtnWidth - 44
                        val actionBtnY = clickY + 3

                        if (mouseX >= actionBtnX && mouseX <= actionBtnX + actionBtnWidth && mouseY >= actionBtnY && mouseY <= actionBtnY + 16) {
                            entry.actionButtons.mouseClicked(mouseX, mouseY, mouseButton)
                            return
                        }

                        var hitSetting = false
                        var settingY = clickY + 24
                        for (setting in entry.settings) {
                            if (!setting.visible()) continue
                            if (mouseX >= ex + 12 && mouseX <= ex + colWidth - 12 && mouseY >= settingY && mouseY <= settingY + setting.getHeight()) {
                                setting.mouseClicked(mouseX, mouseY, mouseButton)
                                hitSetting = true
                                break
                            }
                            settingY += setting.getHeight() + 1
                        }
                        if (!hitSetting) {
                            entry.mod.toggle()
                        }
                    }
                    return
                }
                if (entry.col == 0) leftClickY += cardH + 4
                else rightClickY += cardH + 4
            }
        }

        if (searchFocused || searchActive) {
            searchFocused = false
            searchActive = false
            searchText = ""
            searchCursorPos = 0
            searchSelectionStart = -1
            if (categoryList[selectedCategoryIndex] != Category.CONFIG && categoryList[selectedCategoryIndex] != Category.SETTINGS) {
                selectedCategoryIndex = preSearchCategoryIndex
                rebuildModules()
            }
            applyFilter()
        }

        closeAllColorPickers()

        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    private fun handleConfigClicks(mouseX: Int, mouseY: Int, mouseButton: Int) {
        val visible = if (searchActive && !searchText.isEmpty()) filteredConfigs else configEntries

        createConfigEntry!!.mouseClicked(mouseX, mouseY, mouseButton)

        for (entry in visible) {
            if (entry.isHovering(mouseX, mouseY)) {
                entry.mouseClicked(mouseX, mouseY, mouseButton)
                configListDirty = true
                return
            }
        }
    }

    private fun handleSettingsClicks(mouseX: Int, mouseY: Int, mouseButton: Int) {
        for (entry in settingsEntries) {
            if (entry.isHovering(mouseX, mouseY, entry.x, entry.y, entry.width, entry.height)) {
                entry.mouseClicked(mouseX, mouseY, mouseButton)
                return
            }
        }
    }

    override fun mouseClickMove(mouseX: Int, mouseY: Int, clickedMouseButton: Int, timeSinceLastClick: Long) {
        var mouseX = mouseX
        var mouseY = mouseY
        val transformed = transformMouseCoords(mouseX, mouseY)
        mouseX = transformed[0]
        mouseY = transformed[1]

        val visible = if (searchActive && !searchText.isEmpty()) filteredEntries else moduleEntries
        for (entry in visible) {
            for (s in entry.settings) s.mouseDragged(mouseX, mouseY, clickedMouseButton)
        }

        if (categoryList[selectedCategoryIndex] == Category.CONFIG || categoryList[selectedCategoryIndex] == Category.SETTINGS) {
            for (entry in settingsEntries) {
                entry.mouseDragged(mouseX, mouseY, clickedMouseButton)
            }
        }

        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick)
    }

    override fun mouseReleased(mouseX: Int, mouseY: Int, state: Int) {
        var mouseX = mouseX
        var mouseY = mouseY
        val transformed = transformMouseCoords(mouseX, mouseY)
        mouseX = transformed[0]
        mouseY = transformed[1]

        val visible = if (searchActive && !searchText.isEmpty()) filteredEntries else moduleEntries
        for (entry in visible) {
            for (s in entry.settings) s.mouseReleased(mouseX, mouseY, state)
        }

        if (categoryList[selectedCategoryIndex] == Category.CONFIG || categoryList[selectedCategoryIndex] == Category.SETTINGS) {
            for (entry in settingsEntries) {
                entry.mouseReleased(mouseX, mouseY, state)
            }
        }

        super.mouseReleased(mouseX, mouseY, state)
    }

    @Throws(IOException::class)
    override fun handleMouseInput() {
        val delta = Mouse.getEventDWheel()
        if (delta != 0) {
            var mx = (Mouse.getX() * this.width / this.mc.displayWidth.toDouble()).toInt()
            var mY = (this.height - Mouse.getY() * this.height / this.mc.displayHeight.toDouble() - 1).toInt()

            val transformed = transformMouseCoords(mx, mY)
            mx = transformed[0]
            mY = transformed[1]

            var handled = false
            for (entry in moduleEntries) {
                for (setting in entry.settings) {
                    if (setting is ModeSetting) {
                        if (setting.isExpanded() && setting.isHoveringExpanded(mx, mY)) {
                            setting.handleScroll(delta)
                            handled = true
                            break
                        }
                    }
                }
                if (handled) break
            }

            if (!handled) {
                targetScrollY -= delta * 0.3f
            }
        }
        super.handleMouseInput()
    }

    @Throws(IOException::class)
    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (closing && keyCode != Keyboard.KEY_ESCAPE) return

        if (categoryList[selectedCategoryIndex] == Category.CONFIG) {
            val configHandled = handleConfigKeyInput(typedChar, keyCode)
            if (configHandled) return
        }

        val allEntries = if (searchActive && !searchText.isEmpty()) filteredEntries else moduleEntries
        for (entry in allEntries) {
            if (entry.actionButtons.isBinding && entry.actionButtons.handleKeyInput(keyCode)) {
                return
            }
        }

        if (searchFocused) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                deactivateSearch()
                return
            }
            if (keyCode == Keyboard.KEY_RETURN) {
                deactivateSearch()
                return
            }
            if (isCtrlKeyDown() && keyCode == Keyboard.KEY_A) {
                searchSelectionStart = 0
                searchCursorPos = searchText.length
                return
            }
            if (isCtrlKeyDown() && keyCode == Keyboard.KEY_C && searchSelectionStart >= 0) {
                val selMin = min(searchSelectionStart, searchCursorPos)
                val selMax = max(searchSelectionStart, searchCursorPos)
                writeClipboard(searchText.substring(selMin, selMax))
                return
            }
            if (isCtrlKeyDown() && keyCode == Keyboard.KEY_V) {
                try {
                    val clipboard = readClipboard()
                    if (clipboard != null) insertText(clipboard)
                } catch (_: Exception) {
                }
                return
            }
            if (keyCode == Keyboard.KEY_LEFT) {
                if (isCtrlKeyDown()) searchCursorPos = moveWordLeft(searchText, searchCursorPos)
                else searchCursorPos = max(0, searchCursorPos - 1)
                if (!isShiftKeyDown()) searchSelectionStart = -1
                else if (searchSelectionStart < 0) searchSelectionStart = searchCursorPos
                return
            }
            if (keyCode == Keyboard.KEY_RIGHT) {
                if (isCtrlKeyDown()) searchCursorPos = moveWordRight(searchText, searchCursorPos)
                else searchCursorPos = min(searchText.length, searchCursorPos + 1)
                if (!isShiftKeyDown()) searchSelectionStart = -1
                else if (searchSelectionStart < 0) searchSelectionStart = searchCursorPos
                return
            }
            if (keyCode == Keyboard.KEY_BACK) {
                if (searchSelectionStart >= 0) {
                    val selMin = min(searchSelectionStart, searchCursorPos)
                    val selMax = max(searchSelectionStart, searchCursorPos)
                    searchText = searchText.substring(0, selMin) + searchText.substring(selMax)
                    searchCursorPos = selMin
                    searchSelectionStart = -1
                } else if (searchCursorPos > 0) {
                    searchText = searchText.substring(0, searchCursorPos - 1) + searchText.substring(searchCursorPos)
                    searchCursorPos--
                }
                backspaceHeld = true
                backspaceRepeatTimer = 0f
                applyFilter()
                return
            }
            if (keyCode == Keyboard.KEY_DELETE && searchSelectionStart >= 0) {
                val selMin = min(searchSelectionStart, searchCursorPos)
                val selMax = max(searchSelectionStart, searchCursorPos)
                searchText = searchText.substring(0, selMin) + searchText.substring(selMax)
                searchCursorPos = selMin
                searchSelectionStart = -1
                applyFilter()
                return
            }
            if (Character.isISOControl(typedChar)) {
                super.keyTyped(typedChar, keyCode)
                return
            }
            if (searchSelectionStart >= 0) {
                val selMin = min(searchSelectionStart, searchCursorPos)
                val selMax = max(searchSelectionStart, searchCursorPos)
                searchText = searchText.substring(0, selMin) + typedChar + searchText.substring(selMax)
                searchCursorPos = selMin + 1
                searchSelectionStart = -1
            } else {
                searchText =
                    searchText.substring(0, searchCursorPos) + typedChar + searchText.substring(searchCursorPos)
                searchCursorPos++
            }
            applyFilter()
            return
        }

        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_F) {
            if (!searchActive) {
                preSearchCategoryIndex = selectedCategoryIndex
            }
            searchFocused = true
            searchActive = true
            return
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (!closing) {
                closing = true
            }
            return
        }

        super.keyTyped(typedChar, keyCode)
    }

    private fun checkBackspaceReleased() {
        if (backspaceHeld && !Keyboard.isKeyDown(Keyboard.KEY_BACK)) {
            backspaceHeld = false
            backspaceRepeatTimer = 0f
        }
    }

    private fun deactivateSearch() {
        searchFocused = false
        searchActive = false
        searchText = ""
        searchCursorPos = 0
        searchSelectionStart = -1
        filteredEntries.clear()
        if (categoryList[selectedCategoryIndex] != Category.CONFIG && categoryList[selectedCategoryIndex] != Category.SETTINGS) {
            selectedCategoryIndex = preSearchCategoryIndex
            rebuildModules()
        }
    }

    private fun insertText(text: String) {
        if (searchSelectionStart >= 0) {
            val selMin = min(searchSelectionStart, searchCursorPos)
            val selMax = max(searchSelectionStart, searchCursorPos)
            searchText = searchText.substring(0, selMin) + text + searchText.substring(selMax)
            searchCursorPos = selMin + text.length
            searchSelectionStart = -1
        } else {
            searchText = searchText.substring(0, searchCursorPos) + text + searchText.substring(searchCursorPos)
            searchCursorPos += text.length
        }
        applyFilter()
    }

    private fun handleConfigKeyInput(typedChar: Char, keyCode: Int): Boolean {
        if (createConfigEntry!!.isFocused()) {
            createConfigEntry!!.handleKeyTyped(typedChar, keyCode)
            if (!createConfigEntry!!.isFocused()) {
                configListDirty = true
            }
            return true
        }

        return false
    }

    private fun applyFilter() {
        filteredEntries.clear()
        if (searchText.isEmpty()) return

        val lower = searchText.lowercase(Locale.getDefault())

        val selectedCat = categoryList[selectedCategoryIndex]
        if (selectedCat == Category.CONFIG || selectedCat == Category.SETTINGS) {
            for (entry in moduleEntries) {
                if (entry.mod.getName().lowercase(Locale.getDefault()).contains(lower)) {
                    filteredEntries.add(entry)
                }
            }
        } else {
            for (entry in allModuleEntries) {
                if (entry.mod.getName().lowercase(Locale.getDefault()).contains(lower)) {
                    filteredEntries.add(entry)
                }
            }
        }

        filteredConfigs.clear()
        for (entry in configEntries) {
            if (entry.configName.lowercase(Locale.getDefault()).contains(lower)) {
                filteredConfigs.add(entry)
            }
        }

        targetScrollY = 0f
    }

    private fun moveWordLeft(text: String, pos: Int): Int {
        var pos = pos
        if (pos <= 0) return 0
        do pos--
        while (pos > 0 && !Character.isWhitespace(text[pos - 1]))
        return pos
    }

    private fun moveWordRight(text: String, pos: Int): Int {
        var pos = pos
        if (pos >= text.length) return text.length
        while (pos < text.length && !Character.isWhitespace(text[pos])) pos++
        while (pos < text.length && Character.isWhitespace(text[pos])) pos++
        return pos
    }

    private fun readClipboard(): String? {
        try {
            return Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String?
        } catch (_: Exception) {
            return ""
        }
    }

    private fun writeClipboard(text: String?) {
        try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        } catch (_: Exception) {
        }
    }

    private fun calculateMaxScroll(areaHeight: Float, entries: MutableList<ModuleEntry>): Float {
        var leftTotal = 0f
        var rightTotal = 0f
        for (e in entries) {
            if (e.col == 0) leftTotal += e.totalHeight + 4
            else rightTotal += e.totalHeight + 4
        }
        val maxCol = max(leftTotal, rightTotal)
        val result = maxCol - areaHeight
        return if (result > 0) result else 0f
    }

    private fun rebuildAllModules() {
        allModuleEntries.clear()
        val allMods: MutableList<Module?> = ArrayList<Module?>()
        for (mod in Unfair.moduleManager!!.modules.values) {
            if (mod.getCategory() != Category.CONFIG && mod.getCategory() != Category.SETTINGS) {
                allMods.add(mod)
            }
        }
        for (i in allMods.indices) {
            allModuleEntries.add(ModuleEntry(allMods[i]!!, i % 2))
        }
    }

    private fun rebuildModules() {
        moduleEntries.clear()
        filteredEntries.clear()

        val selectedCat = categoryList[selectedCategoryIndex]

        if (selectedCat == Category.CONFIG || selectedCat == Category.SETTINGS) {
            refreshConfigs()
            targetScrollY = 0f
            scrollY = 0f
            return
        }

        val mods: MutableList<Module?> = ArrayList<Module?>()
        for (mod in Unfair.moduleManager!!.modules.values) {
            if (mod.getCategory() == selectedCat) mods.add(mod)
        }
        for (i in mods.indices) {
            moduleEntries.add(ModuleEntry(mods[i]!!, i % 2))
        }
        targetScrollY = 0f
        scrollY = 0f
    }

    private fun resetConfigAnimations() {
        createConfigEntry!!.setFocused(false)

        for (entry in configEntries) {
            entry.resetAnimations()
        }
    }

    private fun transformMouseCoords(mouseX: Int, mouseY: Int): IntArray {
        val scale = ClientSettings.INSTANCE.guiScale
        val scaledWidth: Float = PANEL_WIDTH * scale
        val scaledHeight: Float = PANEL_HEIGHT * scale

        val screenPanelX = (this.width - scaledWidth) / 2f
        val screenPanelY = (this.height - scaledHeight) / 2f

        val transformedX = ((mouseX - screenPanelX) / scale + screenPanelX).toInt()
        val transformedY = ((mouseY - screenPanelY) / scale + screenPanelY).toInt()

        return intArrayOf(transformedX, transformedY)
    }

    private fun applyScissor(logicX: Float, logicY: Float, logicWidth: Float, logicHeight: Float) {
        val guiScale = ClientSettings.INSTANCE.guiScale
        val panelLogicW: Float = PANEL_WIDTH * guiScale
        val panelLogicH: Float = PANEL_HEIGHT * guiScale
        val panelX = (this.width - panelLogicW) / 2f
        val panelY = (this.height - panelLogicH) / 2f

        val mcX = panelX + (logicX - panelX) * guiScale
        val mcY = panelY + (logicY - panelY) * guiScale
        val mcW = logicWidth * guiScale
        val mcH = logicHeight * guiScale

        if (mcW < 0 || mcH < 0) {
            return
        }

        RenderUtil.scissor(mcX.toDouble(), mcY.toDouble(), mcW.toDouble(), mcH.toDouble())
    }

    private fun closeAllColorPickers() {
        for (entry in moduleEntries) {
            for (setting in entry.settings) {
                if (setting is ColorSetting) {
                    setting.closePicker()
                }
            }
        }
    }

    private fun refreshConfigs() {
        configEntries.clear()
        filteredConfigs.clear()

        val dir = File("./config/Unfair/")
        if (!dir.exists() || !dir.isDirectory()) {
            dir.mkdirs()
            return
        }

        val files = dir.listFiles { _: File?, name: String? -> name!!.endsWith(".json") }
        if (files == null) return

        val seen: MutableSet<String?> = HashSet<String?>()
        for (file in files) {
            try {
                val canonical = file.getCanonicalPath().lowercase(Locale.getDefault())
                if (!canonical.endsWith(".json") || seen.contains(canonical)) continue
                seen.add(canonical)
                configEntries.add(ConfigEntry(file.getName()))
            } catch (_: Exception) {
            }
        }

        configListDirty = false
        if (searchActive && !searchText.isEmpty()) {
            applyFilter()
        }
    }

    private fun refreshSettings() {
        settingsEntries.clear()
        settingsEntries.add(SettingsEntry("Theme", SettingsEntry.SettingsType.THEME))
        settingsEntries.add(SettingsEntry("Background Alpha", SettingsEntry.SettingsType.BACKGROUND_ALPHA))
        settingsEntries.add(SettingsEntry("Blur Area", SettingsEntry.SettingsType.BLUR_AREA))
    }

    private class ModuleEntry(var mod: Module, var col: Int) {
        var settings: MutableList<PanelValueItem> = ArrayList<PanelValueItem>()
        var actionButtons: ModuleActionButtons = ModuleActionButtons(mod)
        var x: Float = 0f
        var width: Float = 0f
        var toggleAnim: Float = if (mod.isEnabled) 1f else 0f
        var hideAnim: Float = if (mod.isHidden) 1f else 0f

        init {
            val props: MutableList<Property<*>?>? = Unfair.propertyManager!!.properties[mod.javaClass]
            if (props != null) {
                for (p in props) {
                    var setting: PanelValueItem? = null
                    if (p is BooleanProperty) setting = BoolSetting(p)
                    else if (p is RangeProperty) setting = RangeSliderSetting(p)
                    else if (p is FloatProperty || p is IntProperty || p is PercentProperty) setting = SliderSetting(p)
                    else if (p is ModeProperty) {
                        val modeSetting = ModeSetting(p)
                        modeSetting.col = col
                        setting = modeSetting
                    } else if (p is ColorProperty) setting = ColorSetting(p)

                    if (setting != null) {
                        setting.initVisibility(p!!.isVisible)
                        settings.add(setting)
                    }
                }
            }
        }

        val totalHeight: Float
            get() {
                var h = 24f
                for (s in settings) {
                    val visAlpha = s.visibilityAlpha
                    if (visAlpha > 0.001f) {
                        h += s.getHeight() * visAlpha + 1
                    }
                }
                return max(h, 40f)
            }
    }

    override fun onGuiClosed() {
        EventManager.unregister(this)
    }

    override fun doesGuiPauseGame(): Boolean {
        return false
    }

    companion object {
        private const val PANEL_WIDTH = 708f
        private const val PANEL_HEIGHT = 448f
        private const val SIDEBAR_WIDTH = 150f
        private const val RADIUS = 10f
        private const val MODULE_CAT_H = 20f
        private const val CONFIG_DOWN_OFFSET = 24f
        private const val CLIENT_TITLE_SPACING = 10f
        private const val NONCONFIG_SEL_BG_UP = 1f
        private const val COL_GAP = 7f

        private const val BACKSPACE_INITIAL_DELAY = 0.4f
        private const val BACKSPACE_REPEAT_RATE = 0.05f

        private const val GUI_ANIM_SPEED = 0.12f

        private fun blendColor(a: Color, b: Color, t: Float): Color {
            val r = (a.red + (b.red - a.red) * t).toInt()
            val g = (a.green + (b.green - a.green) * t).toInt()
            val bl = (a.blue + (b.blue - a.blue) * t).toInt()
            val al = (a.alpha + (b.alpha - a.alpha) * t).toInt()
            return Color(r, g, bl, al)
        }

        private fun blendAlpha(c: Color, alpha: Float): Color {
            return Color(c.red, c.green, c.blue, (alpha * 255).toInt())
        }
    }
}
