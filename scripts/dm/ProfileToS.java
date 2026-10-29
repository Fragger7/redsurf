package androidx.profileinstaller;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Transcodes the baseline profile AGP packs into an APK (format 010, plus its .profm metadata)
 * into ART's current format 015, the only one Android 12+ accepts inside a .dm. On a device
 * ProfileInstaller does this same step at first launch; a .dm has to carry the result already.
 *
 * Lives in androidx.profileinstaller because the transcoder is package-private; it mirrors
 * DeviceProfileWriter.read()/transcodeIfNeeded()/write(). Run with profileinstaller's classes.jar
 * on the classpath (see scripts/make-dm.sh).
 *
 * Usage: ProfileToS <baseline.prof> <baseline.profm> <out primary.prof>
 */
public final class ProfileToS {
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("usage: ProfileToS <baseline.prof> <baseline.profm> <out.prof>");
            System.exit(2);
        }
        DexProfileData[] data;
        try (InputStream in = new FileInputStream(args[0])) {
            byte[] version = ProfileTranscoder.readHeader(in, ProfileTranscoder.MAGIC_PROF);
            data = ProfileTranscoder.readProfile(in, version, "base.apk");
        }
        try (InputStream in = new FileInputStream(args[1])) {
            byte[] metaVersion = ProfileTranscoder.readHeader(in, ProfileTranscoder.MAGIC_PROFM);
            data = ProfileTranscoder.readMeta(in, metaVersion, ProfileVersion.V015_S, data);
        }
        try (OutputStream out = new FileOutputStream(args[2])) {
            ProfileTranscoder.writeHeader(out, ProfileVersion.V015_S);
            if (!ProfileTranscoder.transcodeAndWriteBody(out, ProfileVersion.V015_S, data)) {
                System.err.println("ProfileToS: transcoder refused the profile");
                System.exit(1);
            }
        }
        System.out.println("ProfileToS: " + data.length + " dex file(s) -> format 015");
    }
}
