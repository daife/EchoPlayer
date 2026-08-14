package com.echoplayer.manager;

import com.echoplayer.entity.EchoServerPlayer;
import java.util.function.BiConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import net.minecraft.server.level.ServerPlayer;

class StateSyncHelper {

    static void syncFloatField(ServerPlayer realPlayer, EchoServerPlayer echoPlayer,
                                ToDoubleFunction<ServerPlayer> realGetter,
                                ToDoubleFunction<EchoServerPlayer> echoGetter,
                                BiConsumer<ServerPlayer, Double> realSetter,
                                BiConsumer<EchoServerPlayer, Double> echoSetter,
                                DoubleSupplier lastSupplier,
                                java.util.function.DoubleConsumer lastUpdater) {
        double realValue = realGetter.applyAsDouble(realPlayer);
        double echoValue = echoGetter.applyAsDouble(echoPlayer);
        double lastValue = lastSupplier.getAsDouble();
        boolean echoChanged = Double.compare(echoValue, lastValue) != 0;
        if (echoChanged) {
            realSetter.accept(realPlayer, echoValue);
            lastUpdater.accept(echoValue);
        } else {
            boolean realChanged = Double.compare(realValue, lastValue) != 0;
            if (realChanged) {
                echoSetter.accept(echoPlayer, realValue);
                lastUpdater.accept(realValue);
            }
        }
    }

    static void syncIntField(ServerPlayer realPlayer, EchoServerPlayer echoPlayer,
                              ToIntFunction<ServerPlayer> realGetter,
                              ToIntFunction<EchoServerPlayer> echoGetter,
                              BiConsumer<ServerPlayer, Integer> realSetter,
                              BiConsumer<EchoServerPlayer, Integer> echoSetter,
                              IntSupplier lastSupplier,
                              java.util.function.IntConsumer lastUpdater) {
        int realValue = realGetter.applyAsInt(realPlayer);
        int echoValue = echoGetter.applyAsInt(echoPlayer);
        int lastValue = lastSupplier.getAsInt();
        boolean echoChanged = echoValue != lastValue;
        if (echoChanged) {
            realSetter.accept(realPlayer, echoValue);
            lastUpdater.accept(echoValue);
        } else {
            boolean realChanged = realValue != lastValue;
            if (realChanged) {
                echoSetter.accept(echoPlayer, realValue);
                lastUpdater.accept(realValue);
            }
        }
    }

    private StateSyncHelper() {
    }
}