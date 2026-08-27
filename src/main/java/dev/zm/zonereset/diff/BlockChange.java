// file: BlockChange.java
// description: Wrapper for a BlockRecord used in the diff system.

package dev.zm.zonereset.diff;

import dev.zm.zonereset.snapshot.BlockRecord;

public record BlockChange(BlockRecord record) {

    public long packedPos() {
        return record.packedPos;
    }
} 