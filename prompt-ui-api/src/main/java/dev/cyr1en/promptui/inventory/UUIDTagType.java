package dev.cyr1en.promptui.inventory;

import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * PersistentDataType for storing {@link UUID} values in item {@code PersistentDataContainer}s.
 *
 * <p>Used by {@link dev.cyr1en.promptui.gui.GuiItem} to embed a UUID for click identification.
 * Converts between UUID and a 16-byte array representing the most and least significant bits.</p>
 */
public final class UUIDTagType implements PersistentDataType<byte[], UUID> {

    public static final UUIDTagType INSTANCE = new UUIDTagType();

    private UUIDTagType() {}

    @Override
    public @NotNull Class<byte[]> getPrimitiveType() {
        return byte[].class;
    }

    @Override
    public @NotNull Class<UUID> getComplexType() {
        return UUID.class;
    }

    @Override
    public byte @NotNull [] toPrimitive(@NotNull UUID complex, @NotNull PersistentDataAdapterContext context) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(complex.getMostSignificantBits());
        buffer.putLong(complex.getLeastSignificantBits());
        return buffer.array();
    }

    @Override
    public @NotNull UUID fromPrimitive(byte @NotNull [] primitive, @NotNull PersistentDataAdapterContext context) {
        ByteBuffer buffer = ByteBuffer.wrap(primitive);
        long mostSignificantBits = buffer.getLong();
        long leastSignificantBits = buffer.getLong();
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
