
package org.mwg.core.chunk.heap;

import org.mwg.Callback;
import org.mwg.Graph;
import org.mwg.chunk.StateChunk;
import org.mwg.core.BlackHoleStorage;
import org.mwg.core.CoreConstants;
import org.mwg.core.chunk.Stack;
import org.mwg.utility.HashHelper;
import org.mwg.utility.KeyHelper;
import org.mwg.chunk.Chunk;
import org.mwg.chunk.ChunkSpace;
import org.mwg.chunk.ChunkType;
import org.mwg.struct.Buffer;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class HeapChunkSpace implements ChunkSpace {

    private static final int HASH_LOAD_FACTOR = 4;

    private final int _maxEntries;
    private final int _hashEntries;
    private final int _saveBatchSize;

    private final AtomicInteger _elementCount;

    private final Stack _lru;
    private final Stack _dirtiesStack;

    private final int[] _hashNext;
    private final int[] _hash;
    private final long[] _chunkWorlds;
    private final long[] _chunkTimes;
    private final long[] _chunkIds;
    private final byte[] _chunkTypes;
    private final Chunk[] _chunkValues;
    private final long[] _chunkMarks;
    private final boolean[] _dirties;

    private final Graph _graph;

    @Override
    public final Graph graph() {
        return this._graph;
    }

    final long worldByIndex(long index) {
        return this._chunkWorlds[(int) index];
    }

    final long timeByIndex(long index) {
        return this._chunkTimes[(int) index];
    }

    final long idByIndex(long index) {
        return this._chunkIds[(int) index];
    }

    public final Chunk[] getValues() {
        return _chunkValues;
    }

    public HeapChunkSpace(final int initialCapacity, final int saveBatchSize, final Graph p_graph) {
        _graph = p_graph;
        if (saveBatchSize > initialCapacity) {
            throw new RuntimeException("Save Batch Size can't be bigger than cache size");
        }
        _maxEntries = initialCapacity;
        _hashEntries = initialCapacity * HASH_LOAD_FACTOR;
        _saveBatchSize = saveBatchSize;
        _lru = new FixedStack(initialCapacity, true);
        _dirtiesStack = new FixedStack(initialCapacity, true);
        _hashNext = new int[initialCapacity];
        Arrays.fill(_hashNext, 0, _maxEntries, -1);
        _chunkValues = new Chunk[initialCapacity];
        Arrays.fill(_chunkValues, 0, _maxEntries, null);
        _elementCount = new AtomicInteger(0);
        _hash = new int[_hashEntries];
        Arrays.fill(_hash, 0, _hashEntries, -1);
        _chunkWorlds = new long[_maxEntries];
        Arrays.fill(_chunkWorlds, 0, _maxEntries, -1);
        _chunkTimes = new long[_maxEntries];
        Arrays.fill(_chunkTimes, 0, _maxEntries, -1);
        _chunkIds = new long[_maxEntries];
        Arrays.fill(_chunkIds, 0, _maxEntries, -1);
        _chunkTypes = new byte[_maxEntries];
        Arrays.fill(_chunkTypes, 0, _maxEntries, (byte) -1);
        _chunkMarks = new long[_maxEntries];
        Arrays.fill(_chunkMarks, 0, _maxEntries, 0);
        _dirties = new boolean[_maxEntries];
    }

    @Override
    public final Chunk getAndMark(final byte type, final long world, final long time, final long id) {
        final int index = (int) HashHelper.tripleHash(type, world, time, id, this._hashEntries);
        int m = this._hash[index];
        int found = -1;
        while (m != -1) {
            if (_chunkTypes[m] == type
                    && _chunkWorlds[m] == world
                    && _chunkTimes[m] == time
                    && _chunkIds[m] == id) {
                if (mark(m) > 0) {
                    found = m;
                }
                break;
            } else {
                m = this._hashNext[m];
            }
        }
        if (found != -1) {
            return this._chunkValues[found];
        } else {
            return null;
        }
    }

    @Override
    public final Chunk get(final long index) {
        return this._chunkValues[(int) index];
    }

    @Override
    public final void getOrLoadAndMark(final byte type, final long world, final long time, final long id, final Callback<Chunk> callback) {
        final Chunk fromMemory = getAndMark(type, world, time, id);
        if (fromMemory != null) {
            callback.on(fromMemory);
        } else {
            final Buffer keys = graph().newBuffer();
            KeyHelper.keyToBuffer(keys, type, world, time, id);
            graph().storage().get(keys, new Callback<Buffer>() {
                @Override
                public void on(final Buffer result) {
                    if (result != null) {
                        Chunk loadedChunk = createAndMark(type, world, time, id);
                        loadedChunk.load(result);
                        result.free();
                        callback.on(loadedChunk);
                    } else {
                        keys.free();
                        callback.on(null);
                    }
                }
            });
        }
    }

    //TODO change this with a compareAndSwap
    @Override
    public long mark(long index) {
        long marks = _chunkMarks[(int) index];
        if (marks != -1) {
            marks++;
            _chunkMarks[(int) index] = marks;
        }
        if (marks == 1) {
            //was at zero before, risky operation, check selectWith LRU
            this._lru.dequeue(index);
        }
        return marks;
    }

    @Override
    public void unmark(long index) {
        long marks = _chunkMarks[(int) index];
        if (marks != -1) {
            marks--;
            _chunkMarks[(int) index] = marks;
        }
        if (marks == 0) {
            //declare available for recycling
            this._lru.enqueue(index);
        }
    }

    @Override
    public void free(Chunk chunk) {
        //NOOP
    }

    /*
    @Override
    public Chunk create(byte p_type, long p_world, long p_time, long p_id, Buffer p_initialPayload, Chunk origin) {
        switch (p_type) {
            case ChunkType.STATE_CHUNK:
                return new HeapStateChunk(this, p_initialPayload, origin);
            case ChunkType.WORLD_ORDER_CHUNK:
                return new HeapWorldOrderChunk(this, p_initialPayload);
            case ChunkType.TIME_TREE_CHUNK:
                return new HeapTimeTreeChunk(this, p_initialPayload);
            case ChunkType.GEN_CHUNK:
                return new HeapGenChunk(this, p_id, p_initialPayload);
        }
        return null;
    }
    */

    //TODO, this method has performance issue
    @Override
    public Chunk createAndMark(final byte type, final long world, final long time, final long id) {
        //first mark the object
        int entry = -1;
        int hashIndex = (int) HashHelper.tripleHash(type, world, time, id, this._hashEntries);
        int m = this._hash[hashIndex];
        while (m >= 0) {
            if (type == _chunkTypes[m] && world == _chunkWorlds[m] && time == _chunkTimes[m] && id == _chunkIds[m]) {
                entry = m;
                break;
            }
            m = this._hashNext[m];
        }
        if (entry == -1) {

            //we look for nextIndex
            int currentVictimIndex = (int) this._lru.dequeueTail();
            if (currentVictimIndex == -1) {
                //TODO cache is full :(
                System.gc();
                try {
                    System.err.println("GC failback...");
                    Thread.sleep(100);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                currentVictimIndex = (int) this._lru.dequeueTail();
                if (currentVictimIndex == -1) {
                    throw new RuntimeException("mwDB crashed, cache is full, please avoid to much retention of nodes or augment cache capacity!");
                }
            }

            Chunk toInsert = null;
            switch (type) {
                case ChunkType.STATE_CHUNK:
                    toInsert = new HeapStateChunk(this, currentVictimIndex);
                    break;
                case ChunkType.WORLD_ORDER_CHUNK:
                    toInsert = new HeapWorldOrderChunk(this, currentVictimIndex);
                    break;
                case ChunkType.TIME_TREE_CHUNK:
                    toInsert = new HeapTimeTreeChunk(this, currentVictimIndex);
                    break;
                case ChunkType.GEN_CHUNK:
                    toInsert = new HeapGenChunk(this, id, currentVictimIndex);
                    break;
            }

            if (this._chunkValues[currentVictimIndex] != null) {
                // Chunk victim = this._chunkValues[currentVictimIndex];
                final long victimWorld = _chunkWorlds[currentVictimIndex];
                final long victimTime = _chunkTimes[currentVictimIndex];
                final long victimObj = _chunkIds[currentVictimIndex];
                final byte victimType = _chunkTypes[currentVictimIndex];
                final int indexVictim = (int) HashHelper.tripleHash(victimType, victimWorld, victimTime, victimObj, this._hashEntries);

                m = _hash[indexVictim];
                int last = -1;
                while (m >= 0) {
                    if (victimType == _chunkTypes[m] && victimWorld == _chunkWorlds[m] && victimTime == _chunkTimes[m] && victimObj == _chunkIds[m]) {
                        break;
                    }
                    last = m;
                    m = _hashNext[m];
                }
                //POP THE VALUE FROM THE NEXT LIST
                if (last == -1) {
                    int previousNext = _hashNext[m];
                    _hash[indexVictim] = previousNext;
                } else {
                    if (m == -1) {
                        _hashNext[last] = -1;
                    } else {
                        _hashNext[last] = _hashNext[m];
                    }
                }
                _hashNext[m] = -1;//flag to dropped value
                //UNREF victim value object
                _chunkValues[currentVictimIndex] = null;
                //TODO compare and swap here
                _chunkMarks[currentVictimIndex] = -1;

                //free the lock
                this._elementCount.decrementAndGet();
            }

            _chunkValues[currentVictimIndex] = toInsert;
            _chunkMarks[currentVictimIndex] = 1;
            _chunkTypes[currentVictimIndex] = type;
            _chunkWorlds[currentVictimIndex] = world;
            _chunkTimes[currentVictimIndex] = time;
            _chunkIds[currentVictimIndex] = id;

            //negociate the lock to write on hashIndex
            _hashNext[currentVictimIndex] = _hash[hashIndex];
            _hash[hashIndex] = currentVictimIndex;
            //free the lock
            this._elementCount.incrementAndGet();
            return toInsert;
        } else {
            _chunkMarks[entry] = _chunkMarks[entry] + 1;
            return _chunkValues[entry];
        }
    }

    //TODO change by a compare and swap
    @Override
    public void notifyUpdate(long index) {
        boolean previous = _dirties[(int) index];
        if (!previous) {
            _dirties[(int) index] = true;
            _dirtiesStack.enqueue(index);
            if (_dirtiesStack.size() > _saveBatchSize) {
                save();
            }
        }
    }

    @Override
    public void save() {
        boolean isNoop = this._graph.storage() instanceof BlackHoleStorage;
        final Buffer stream = this._graph.newBuffer();
        boolean isFirst = true;
        while (_dirtiesStack.size() != 0) {
            long tail = _dirtiesStack.dequeueTail();
            Chunk loopChunk = _chunkValues[(int) tail];
            //Save chunk Key
            if (!isNoop) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    stream.write(CoreConstants.BUFFER_SEP);
                }
                KeyHelper.keyToBuffer(stream, loopChunk.chunkType(), loopChunk.world(), loopChunk.time(), loopChunk.id());
            }
            //Save chunk payload
            stream.write(CoreConstants.BUFFER_SEP);
            try {
                if (!isNoop) { //optimization to not save unused bytes
                    loopChunk.save(stream);
                }
                _dirties[(int) tail] = false;
                unmark((int) tail);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //shrink in case of i != full size
        this.graph().storage().put(stream, new Callback<Boolean>() {
            @Override
            public void on(Boolean result) {
                //free all value
                stream.free();
            }
        });
    }

    @Override
    public final void clear() {
        //TODO reset everything
    }

    @Override
    public final void freeAll() {
        //TODO reset everything
    }

    @Override
    public final long size() {
        return this._elementCount.get();
    }

    @Override
    public final long available() {
        return _lru.size();
    }

    public final void printMarked() {
        for (int i = 0; i < _chunkValues.length; i++) {
            if (_chunkValues[i] != null) {
                if (_chunkMarks[i] != 0) {
                    switch (_chunkTypes[i]) {
                        case ChunkType.STATE_CHUNK:
                            System.out.println("STATE(" + _chunkWorlds[i] + "," + _chunkValues[i].time() + "," + _chunkValues[i].id() + ")->marks->" + _chunkMarks[i]);
                            break;
                        case ChunkType.TIME_TREE_CHUNK:
                            System.out.println("TIME_TREE(" + _chunkWorlds[i] + "," + _chunkValues[i].time() + "," + _chunkValues[i].id() + ")->marks->" + _chunkMarks[i]);
                            break;
                        case ChunkType.WORLD_ORDER_CHUNK:
                            System.out.println("WORLD_ORDER(" + _chunkWorlds[i] + "," + _chunkValues[i].time() + "," + _chunkValues[i].id() + ")->marks->" + _chunkMarks[i]);
                            break;
                        case ChunkType.GEN_CHUNK:
                            System.out.println("GENERATOR(" + _chunkWorlds[i] + "," + _chunkValues[i].time() + "," + _chunkValues[i].id() + ")->marks->" + _chunkMarks[i]);
                            break;
                    }
                }
            }
        }
    }

}



