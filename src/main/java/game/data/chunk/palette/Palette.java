package game.data.chunk.palette;

import game.data.WorldManager;
import game.data.chunk.Chunk;
import packets.DataTypeProvider;
import se.llbit.nbt.ListTag;
import se.llbit.nbt.SpecificTag;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to hold a palette of a chunk.
 */
public class Palette {
    private static boolean maskBedrock = false;
    protected int bitsPerBlock;
    private int[] palette;

    protected Palette() { }

    private Palette(int bitsPerBlock, int[] palette) {
        this.bitsPerBlock = bitsPerBlock;
        this.palette = palette;

        synchronizeBitsPerBlock();
    }

    /**
     * Some non-vanilla servers will use more bits per block than needed, which will cause
     * issues when reading in the chunk later. To fix this, we increase the size of the
     * palette array by by adding unused block states.
     */
    private void synchronizeBitsPerBlock() {
        if (this.bitsPerBlock > 16) {
            throw new IllegalArgumentException("Bits per block may not be more than 16. Given: " + this.bitsPerBlock);
        }

        while (this.bitsPerBlock > computeBitsPerBlock(palette.length - 1)) {
            int[] newPalette = new int[palette.length + 1];
            System.arraycopy(palette, 0, newPalette, 0, palette.length);
            this.palette = newPalette;
        }
    }

    public Palette(ListTag nbt) {
        this.bitsPerBlock = computeBitsPerBlock(nbt.size() - 1);
        this.palette = new int[nbt.size()];

        GlobalPalette global = WorldManager.getGlobalPalette();
        for (int i = 0; i < nbt.size(); i++) {
            BlockState bs = global.getState(nbt.get(i).get("Name").stringValue());
            this.palette[i] = bs.getNumericId();
        }
    }

    private int computeBitsPerBlock(int maxIndex) {
        int bitsNeeded = Integer.SIZE - Integer.numberOfLeadingZeros(maxIndex);
        return Math.max(4, bitsNeeded);
    }


    public static void setMaskBedrock(boolean maskBedrock) {
        Palette.maskBedrock = maskBedrock;
    }

    /**
     * Read the palette from the network stream.
     * @param bitsPerBlock the number of bits per block that is used, indicates the palette type
     * @param dataTypeProvider network stream reader
     */
    public static Palette readPalette(int bitsPerBlock, DataTypeProvider dataTypeProvider) {
        int size = dataTypeProvider.readVarInt();

        int[] palette = dataTypeProvider.readVarIntArray(size);

        if (maskBedrock) {
            for (int i = 0; i < palette.length; i++) {
                if (palette[i] == 0x70) {
                    palette[i] = 0x10;
                }
            }
        }

        return new Palette(bitsPerBlock, palette);
    }

    /**
     * Get the block state from the palette index.
     */
    public int stateFromId(int index) {
        if (bitsPerBlock > 8) {
            return index;
        }
        if (palette.length == 0) {
            return 0;
        }
        if (index >= palette.length) {
            return 0;
        }

        return palette[index];
    }

    public boolean isEmpty() {
        return palette.length == 0 || (palette.length == 1 && palette[0] == 0);
    }

    /**
     * Create an NBT version of this palette using the global palette.
     */
    public List<SpecificTag> toNbt() {
        List<SpecificTag> tags = new ArrayList<>();
        GlobalPalette globalPalette = WorldManager.getGlobalPalette();

        if (globalPalette == null) {
            throw new UnsupportedOperationException("Cannot create palette NBT without a global palette.");
        }

        for (int i : palette) {
            BlockState state = globalPalette.getState(i);
            if (state == null) { state = globalPalette.getDefaultState(); }

            tags.add(state.toNbt());

        }
        return tags;
    }

    public int getBitsPerBlock() {
        return bitsPerBlock;
    }
}
