package unfair.ui.clickgui.dropdown;

import unfair.Unfair;
import unfair.management.ClientSettings;
import unfair.module.modules.render.HUD;
import unfair.property.properties.RangeProperty;
import unfair.util.shader.RoundedUtils;

import java.awt.Color;

/**
 * 双滑块设置项，对应 {@link RangeProperty}。
 */
public class RangeSliderSetting extends ValueItem {
    private final RangeProperty value;
    // 0=未拖动 1=已按下但方向未定(两把手重合) 2=拖下界 3=拖上界
    private int dragState = 0;
    private int anchorX = 0;

    private static final float SLIDER_HEIGHT = 3;
    private static final float KNOB_SIZE = 8;

    public RangeSliderSetting(RangeProperty v) {
        this.value = v;
    }

    private double range() {
        return this.value.getMaximum() - this.value.getMinimum();
    }

    private float lowPct() {
        double range = this.range();
        return range > 0 ? (float) ((this.value.getLow() - this.value.getMinimum()) / range) : 0;
    }

    private float highPct() {
        double range = this.range();
        return range > 0 ? (float) ((this.value.getHigh() - this.value.getMinimum()) / range) : 0;
    }

    private float valueAtMouse(int mouseX, float sliderX, float sliderW) {
        double pct = Math.max(0, Math.min(1, (mouseX - sliderX) / sliderW));
        float val = (float) (this.value.getMinimum() + pct * this.range());
        return Math.round(val * 100.0F) / 100.0F;
    }

    private static HUD getHud() {
        return (HUD) Unfair.moduleManager.modules.get(HUD.class);
    }

    @Override
    public void render(int mouseX, int mouseY) {
        Unfair.fontManager.getFont(15).drawString(
                this.value.getName(), x + 2, y + 1,
                ClientSettings.INSTANCE.getSettingNameColor().getRGB(), false);

        String displayStr = Math.round(this.value.getLow() * 100.0) / 100.0
                + "-" + Math.round(this.value.getHigh() * 100.0) / 100.0;
        float textWidth = Unfair.fontManager.getFont(15).getStringWidth(displayStr);
        Unfair.fontManager.getFont(15).drawString(
                displayStr, x + width - textWidth - 2, y + 1,
                ClientSettings.INSTANCE.getValueTextColor().getRGB(), false);

        float sliderX = x + 2;
        float sliderW = width - 4;
        float sliderY = y + getHeight() - SLIDER_HEIGHT - 6;

        // 轨道
        RoundedUtils.drawRound(sliderX, sliderY, sliderW, SLIDER_HEIGHT, SLIDER_HEIGHT / 2f,
                ClientSettings.INSTANCE.getSliderTrackColor());

        float lowX = sliderX + lowPct() * sliderW;
        float highX = sliderX + highPct() * sliderW;
        float fillW = Math.max(0, highX - lowX);

        // 选中区间（渐变）
        if (fillW > 1) {
            long time = System.currentTimeMillis();
            HUD hud = getHud();
            Color c1 = hud.getColor(time);
            Color c2 = hud.getColor(time + 200);
            RoundedUtils.drawGradientHorizontal(lowX, sliderY, fillW, SLIDER_HEIGHT, SLIDER_HEIGHT / 2f, c1, c2);
        }

        // 两个把手
        drawKnob(lowX, sliderY);
        drawKnob(highX, sliderY);

        if (this.dragState != 0) {
            this.applyDrag(mouseX);
        }
    }

    /** 根据当前鼠标位置推进拖拽，处理「方向未定」与「越过把手自动切换」 */
    private void applyDrag(int mouseX) {
        float raw = this.valueAtMouse(mouseX, this.x + 2, this.width - 4);
        int state = this.dragState;

        if (state == 1) {
            // 等距按下：按首个移动方向决定拖哪个把手
            if (mouseX > this.anchorX + 1) {
                state = 3;
            } else if (mouseX < this.anchorX - 1) {
                state = 2;
            } else {
                return;
            }
            this.dragState = state;
        }

        if (state == 2) {
            // 下界越过当前上界 → 改为拖上界
            if (raw > this.value.getHigh()) {
                this.dragState = 3;
                this.value.setHigh(raw);
            } else {
                this.value.setLow(raw);
            }
        } else if (state == 3) {
            // 上界越过当前下界 → 改为拖下界
            if (raw < this.value.getLow()) {
                this.dragState = 2;
                this.value.setLow(raw);
            } else {
                this.value.setHigh(raw);
            }
        }
    }

    private void drawKnob(float knobCenterX, float sliderY) {
        float knobX = knobCenterX - KNOB_SIZE / 2f;
        float knobY = sliderY + SLIDER_HEIGHT / 2f - KNOB_SIZE / 2f;
        RoundedUtils.drawRound(knobX, knobY, KNOB_SIZE, KNOB_SIZE, KNOB_SIZE / 2f, new Color(200, 200, 200));
        RoundedUtils.drawRound(knobX + 1.5f, knobY + 1.5f, KNOB_SIZE - 3, KNOB_SIZE - 3, (KNOB_SIZE - 3) / 2f,
                new Color(220, 220, 220));
    }

    @Override
    public void mouseClicked(int mx, int my, int button) {
        float sliderY = y + getHeight() - SLIDER_HEIGHT - 6;
        if (!isHovering(mx, my, x + 2, sliderY - 4, width - 4, SLIDER_HEIGHT + 8) || button != 0) {
            return;
        }

        float sliderX = x + 2;
        float sliderW = width - 4;
        float lowX = sliderX + lowPct() * sliderW;
        float highX = sliderX + highPct() * sliderW;

        float newVal = this.valueAtMouse(mx, sliderX, sliderW);

        // 严格比较距离：只有明显靠近某把手时才直接选中
        boolean closerToLow = Math.abs(mx - lowX) < Math.abs(mx - highX);
        boolean closerToHigh = Math.abs(mx - highX) < Math.abs(mx - lowX);
        this.anchorX = mx;
        if (closerToLow) {
            this.dragState = 2;
            this.value.setLow(newVal);
        } else if (closerToHigh) {
            this.dragState = 3;
            this.value.setHigh(newVal);
        } else {
            // 两把手重合：等首个移动方向再决定，避免死锁
            this.dragState = 1;
        }
    }

    @Override
    public void mouseReleased(int mx, int my, int button) {
        if (button == 0) {
            this.dragState = 0;
        }
    }

    @Override
    public float getHeight() {
        return 24;
    }

    @Override
    public boolean visible() {
        return this.value.isVisible();
    }
}
