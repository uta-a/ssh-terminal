# sshj / BouncyCastle の反射的に参照されるクラスを保持する（R8 縮小を有効化した際に必要）。
-keep class org.bouncycastle.** { *; }
-keep class net.i2p.crypto.eddsa.** { *; }
-keep class com.hierynomus.sshj.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
