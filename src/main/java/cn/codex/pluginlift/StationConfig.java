package cn.codex.pluginlift;

public record StationConfig(String number, String description, boolean ding) {
    public static StationConfig parse(String input, boolean currentDing) {
        String[] fields = input.replace('｜', '|').split("\\|", -1);
        if (fields.length < 2 || fields.length > 3) throw new IllegalArgumentException("格式：楼层编号 | 楼层描述 | 提示音开/关");
        String number = fields[0].trim(), description = fields[1].trim();
        if (number.isEmpty() || number.length() > 8) throw new IllegalArgumentException("楼层编号必须为 1–8 个字符");
        if (description.length() > 128) throw new IllegalArgumentException("楼层描述最多 128 个字符");
        boolean ding = fields.length == 3 && !fields[2].trim().isEmpty() ? parseBoolean(fields[2].trim()) : currentDing;
        return new StationConfig(number, description, ding);
    }
    private static boolean parseBoolean(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "开", "开启", "是", "true", "on", "yes", "1" -> true;
            case "关", "关闭", "否", "false", "off", "no", "0" -> false;
            default -> throw new IllegalArgumentException("提示音只能填写开或关");
        };
    }
}