# Room 生成的实现类需要保留
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# 枚举名在数据里被当作字符串使用（ZoneType.name 等），混淆会把它们改掉
-keepclassmembers enum com.bjlure.app.domain.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public java.lang.String name();
}
