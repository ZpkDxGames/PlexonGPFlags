package com.plexon.gpflags.config;

public final class SchemaVersion {
    private SchemaVersion() {}

    public static int requireSupported(Object raw, int absentVersion, int currentVersion, String label) {
        final int version;
        if (raw == null) {
            version = absentVersion;
        } else if (raw instanceof Number number) {
            double value = number.doubleValue();
            if (!Double.isFinite(value) || Math.rint(value) != value || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(label + " schema-version must be an integer");
            }
            version = (int) value;
        } else {
            throw new IllegalArgumentException(label + " schema-version must be an integer");
        }
        if (version < 1) throw new IllegalArgumentException(label + " schema-version must be >= 1");
        if (version > currentVersion) throw new IllegalStateException(label + " schema " + version + " is newer than supported " + currentVersion);
        return version;
    }
}
