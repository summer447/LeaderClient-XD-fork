package unfair.property.properties;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import unfair.property.Property;

import java.util.function.BooleanSupplier;

/**
 * 双滑块区间属性：在 [minimum, maximum] 范围内维护一个 [low, high] 区间。
 * 对应 GUI 中的 RangeSlider（两个可拖动把手）。
 */
public class RangeProperty extends Property<RangeProperty.Range> {

    /** 不可变区间值 */
    public static final class Range {
        private final float low;
        private final float high;

        public Range(float low, float high) {
            this.low = low;
            this.high = high;
        }

        public float getLow() {
            return this.low;
        }

        public float getHigh() {
            return this.high;
        }
    }

    private final float minimum;
    private final float maximum;

    public RangeProperty(String name, float low, float high, float minimum, float maximum) {
        this(name, low, high, minimum, maximum, null);
    }

    public RangeProperty(String name, float low, float high, float minimum, float maximum, BooleanSupplier visible) {
        super(name, new Range(low, high),
                r -> r.getLow() >= minimum && r.getHigh() <= maximum && r.getLow() <= r.getHigh(),
                visible);
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public float getLow() {
        return this.getValue().getLow();
    }

    public float getHigh() {
        return this.getValue().getHigh();
    }

    public float getMinimum() {
        return this.minimum;
    }

    public float getMaximum() {
        return this.maximum;
    }

    /** 设置区间下界，自动夹取到 [minimum, high] */
    public boolean setLow(float low) {
        low = clamp(low, this.minimum, this.getHigh());
        return this.setValue(new Range(low, this.getHigh()));
    }

    /** 设置区间上界，自动夹取到 [low, maximum] */
    public boolean setHigh(float high) {
        high = clamp(high, this.getLow(), this.maximum);
        return this.setValue(new Range(this.getLow(), high));
    }

    /** 同时设置整个区间，自动夹取并保证 low <= high */
    public boolean setRange(float low, float high) {
        low = clamp(low, this.minimum, this.maximum);
        high = clamp(high, this.minimum, this.maximum);
        if (low > high) {
            low = high;
        }
        return this.setValue(new Range(low, high));
    }

    @Override
    public boolean setValue(Object object) {
        // 防御性拷贝，避免外部持有可变引用
        if (object instanceof Range) {
            Range range = (Range) object;
            return super.setValue(new Range(range.getLow(), range.getHigh()));
        }
        return false;
    }

    @Override
    public String getValuePrompt() {
        return String.format("%s-%s", this.minimum, this.maximum);
    }

    @Override
    public String formatValue() {
        return String.format("&6%s-%s", round2(this.getLow()), round2(this.getHigh()));
    }

    @Override
    public boolean parseString(String string) {
        if (string == null) {
            return false;
        }
        String[] parts = string.split("[-~,]", 2);
        if (parts.length != 2) {
            return false;
        }
        try {
            return this.setRange(Float.parseFloat(parts[0].trim()), Float.parseFloat(parts[1].trim()));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public boolean read(JsonObject jsonObject) {
        JsonElement element = jsonObject.get(this.getName());
        if (element == null || !element.isJsonObject()) {
            return false;
        }
        JsonObject obj = element.getAsJsonObject();
        return this.setRange(obj.get("low").getAsNumber().floatValue(), obj.get("high").getAsNumber().floatValue());
    }

    @Override
    public void write(JsonObject jsonObject) {
        JsonObject obj = new JsonObject();
        obj.addProperty("low", this.getLow());
        obj.addProperty("high", this.getHigh());
        jsonObject.add(this.getName(), obj);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float round2(float value) {
        return Math.round(value * 100.0F) / 100.0F;
    }
}
