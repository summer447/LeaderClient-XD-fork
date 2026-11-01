package unfair.module.modules.render;

import net.minecraft.client.gui.ScaledResolution;
import unfair.event.EventTarget;
import unfair.events.Render2DEvent;
import unfair.module.Module;
import unfair.property.properties.IntProperty;
import unfair.util.NotificationTask;
import unfair.util.RenderUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static unfair.config.Config.mc;

public class Notification extends Module {
    public Notification(){super("Notification",true,true);}
    public static final IntProperty showTime = new IntProperty("Show Time",2000,0,10000);
    private final IntProperty space = new IntProperty("Space",20,0,20);
    public final IntProperty rectLeft = new IntProperty("Rect Left", 0, 0, 20);
    public final IntProperty rectTop = new IntProperty("Rect Top", 50, 0, 100);
    public static final CopyOnWriteArrayList<NotificationTask> tasks = new CopyOnWriteArrayList<>();
    public static final CopyOnWriteArrayList<NotificationTask> disTasks = new CopyOnWriteArrayList<>();

    private float lerp(float current, float target,float speed) {
        return current + (target - current) * speed;
    }
    public static void post(String name) {
        tasks.add(new NotificationTask(name, showTime.getValue()));
    }
    public static void postDis(String name) {
        disTasks.add(new NotificationTask(name, showTime.getValue()));
    }
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;
        ScaledResolution sr = new ScaledResolution(mc);
        float x = sr.getScaledWidth() - rectLeft.getValue();
        float y = sr.getScaledHeight() - rectTop.getValue();

        // merge enable/disable notifications into one time-ordered stack
        List<NotificationTask> merged = new ArrayList<>(tasks);
        merged.addAll(disTasks);
        merged.sort(Comparator.comparingLong(NotificationTask::getStart));
        renderNotificationList(merged, x, y);
    }

    private void renderNotificationList(List<NotificationTask> list, float x, float y) {
        final float HEIGHT = 36;
        final float PADDING = 8;
        final String TITLE = "Module";
        final Color enableColor = new Color(46, 204, 113);
        final Color disableColor = new Color(231, 76, 60);
        for (NotificationTask task : list) {
            Color accentColor = disTasks.contains(task) ? disableColor : enableColor;
            if (task.isFinished()) {
                task.targetX = x + 8;
                if (Math.abs(task.animationX - task.targetX) < 1) {
                    tasks.remove(task);
                    disTasks.remove(task);
                    continue;
                }
            } else {
                float textWidth = Math.max(RenderUtil.getWidth(task.message), RenderUtil.getWidth(TITLE) * 0.75f);
                float cardWidth = Math.max(textWidth + PADDING * 2 + 28, 120);
                task.targetX = x - cardWidth;
            }

            task.animationX = lerp(task.animationX, task.targetX, 0.18f);

            float renderX = task.animationX;
            float width = x - renderX;
            float progress = task.getProgress();
            float progressWidth = Math.max(0, (width - 8) * (1.0f - progress));
            Color progressColor = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 170);
            // soft shadow + dark rounded card
            RenderUtil.drawRoundedRectangle(renderX - 1, y - 1, x + 1, y + HEIGHT + 1, 6.0f, new Color(0, 0, 0, 60).getRGB());
            RenderUtil.drawRoundedRectangle(renderX, y, x, y + HEIGHT, 6.0f, new Color(22, 22, 28, 215).getRGB());
            // subtle top highlight for depth
            RenderUtil.drawRoundedRectangle(renderX + 2, y, x - 2, y + 1, 1.0f, new Color(255, 255, 255, 20).getRGB());
            // title
            RenderUtil.scaleStart(renderX + PADDING, y + 6, 0.75f);
            RenderUtil.drawFont(TITLE, (int) (renderX + PADDING), (int) (y + 6), accentColor.getRGB(), false);
            RenderUtil.scaleEnd();
            // message
            RenderUtil.drawFont(task.message, (int) (renderX + PADDING), (int) (y + 17), -1, true);
            // rounded progress bar
            RenderUtil.drawRoundedRectangle(renderX + 3, y + HEIGHT - 3, renderX + 3 + progressWidth, y + HEIGHT - 1.5f, 1.0f, progressColor.getRGB());
            y -= (HEIGHT + space.getValue());
        }
    }
}