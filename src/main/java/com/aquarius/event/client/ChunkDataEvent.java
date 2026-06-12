package com.aquarius.event.client;

/** A full chunk arrived from the server and is now in the chunk cache. Coordinates are CHUNK coords. */
public record ChunkDataEvent(int x, int z) { }
