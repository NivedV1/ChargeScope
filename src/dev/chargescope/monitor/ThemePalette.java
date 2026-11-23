package dev.chargescope.monitor;

import android.content.Context;
import android.os.Build;

final class ThemePalette {
    final int background;
    final int surface;
    final int primary;
    final int primaryContainer;
    final int onPrimaryContainer;
    final int onSurface;
    final int onSurfaceVariant;
    final int outline;

    private ThemePalette(int background, int surface, int primary, int primaryContainer,
                         int onPrimaryContainer, int onSurface, int onSurfaceVariant, int outline) {
        this.background = background;
        this.surface = surface;
        this.primary = primary;
        this.primaryContainer = primaryContainer;
        this.onPrimaryContainer = onPrimaryContainer;
        this.onSurface = onSurface;
        this.onSurfaceVariant = onSurfaceVariant;
        this.outline = outline;
    }

    static ThemePalette forMode(Context context, boolean dark) {
        if (Build.VERSION.SDK_INT >= 34) {
            return new ThemePalette(
                context.getColor(dark ? android.R.color.system_surface_dark : android.R.color.system_surface_light),
                context.getColor(dark ? android.R.color.system_surface_container_dark : android.R.color.system_surface_container_light),
                context.getColor(dark ? android.R.color.system_primary_dark : android.R.color.system_primary_light),
                context.getColor(dark ? android.R.color.system_primary_container_dark : android.R.color.system_primary_container_light),
                context.getColor(dark ? android.R.color.system_on_primary_container_dark : android.R.color.system_on_primary_container_light),
                context.getColor(dark ? android.R.color.system_on_surface_dark : android.R.color.system_on_surface_light),
                context.getColor(dark ? android.R.color.system_on_surface_variant_dark : android.R.color.system_on_surface_variant_light),
                context.getColor(dark ? android.R.color.system_outline_variant_dark : android.R.color.system_outline_variant_light));
        }
        if (Build.VERSION.SDK_INT >= 31) {
            return new ThemePalette(
                context.getColor(dark ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_10),
                context.getColor(dark ? android.R.color.system_neutral1_800 : android.R.color.system_neutral1_50),
                context.getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600),
                context.getColor(dark ? android.R.color.system_accent1_700 : android.R.color.system_accent1_100),
                context.getColor(dark ? android.R.color.system_accent1_100 : android.R.color.system_accent1_900),
                context.getColor(dark ? android.R.color.system_neutral1_100 : android.R.color.system_neutral1_900),
                context.getColor(dark ? android.R.color.system_neutral2_300 : android.R.color.system_neutral2_700),
                context.getColor(dark ? android.R.color.system_neutral2_700 : android.R.color.system_neutral2_200));
        }

        return dark
            ? new ThemePalette(0xff101513, 0xff1c2420, 0xffa1d9b9, 0xff20513b,
                0xffbfefd1, 0xffe1ebe5, 0xffbfc9c2, 0xff46524b)
            : new ThemePalette(0xfff7faf8, 0xffe9efeb, 0xff176b4b, 0xffbaf0d2,
                0xff062016, 0xff18211c, 0xff425048, 0xffbfc9c2);
    }
}
