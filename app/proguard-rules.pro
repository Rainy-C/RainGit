# JGit uses ServiceLoader for its platform and transport implementations.
-keep class * extends org.eclipse.jgit.util.FS { public <init>(...); }
-keep class * extends org.eclipse.jgit.transport.SshSessionFactory { *; }
-keep class org.eclipse.jgit.transport.TransportHttp { *; }
-keep class org.eclipse.jgit.transport.TransportLocal { *; }
-keep class org.eclipse.jgit.transport.TransportGitAnon { *; }
-keep class org.eclipse.jgit.transport.TransportBundleFile { *; }
-keep class org.eclipse.jgit.transport.HttpAuthMethod$** { *; }
-keep class org.eclipse.jgit.internal.JGitText { public <fields>; }
-keep class * extends org.eclipse.jgit.nls.TranslationBundle { public <fields>; public <init>(); }
-keepattributes Signature,InnerClasses,EnclosingMethod
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.impl.StaticMarkerBinder

-keep class org.eclipse.jgit.transport.HttpTransport { *; }
-keep class org.eclipse.jgit.transport.http.JDKHttpConnectionFactory { *; }
-keep class org.eclipse.jgit.util.sha1.SHA1Java { *; }
-keep class org.slf4j.nop.NOPServiceProvider { public <init>(); *; }
-keep class * implements org.eclipse.jgit.transport.http.HttpConnectionFactory { public <init>(); }

# Desktop JVM monitoring and Kerberos are optional JGit paths, unused by GitHub HTTPS/PAT.
-dontwarn javax.management.**
-dontwarn java.lang.management.**
-dontwarn org.ietf.jgss.**
