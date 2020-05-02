/*
 * MIT License
 *
 * Copyright (c) 2017 Frederik Ar. Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package fredboat.audio.queue

import fredboat.definitions.RepeatMode
import java.util.*

class SimpleTrackProvider : AbstractTrackProvider() {
    private val queue = LinkedList<AudioTrackContext>()
    private var lastTrack: AudioTrackContext? = null
    private var cachedShuffledQueue: List<AudioTrackContext> = ArrayList()
    private var shouldUpdateShuffledQueue = true

    /**this override is needed because, related to the repeat all mode, turning shuffle off, skipping a track, turning shuffle
     *   on will cause an incorrect playlist to show with the list command and may lead to a bug of an
     *   IllegalStateException due to trying to play the same AudioTrack object twice */
    override var isShuffle: Boolean
        get() = super.isShuffle
        set(shuffle) {
            super.isShuffle = shuffle
            if (shuffle) {
                shouldUpdateShuffledQueue = true
                synchronized(this)
                {
                    queue.forEach { it.isPriority = false } // reset all priority tracks
                }
            }
        }

    /**
     * Update the new queue
     * adjust rand values so they are evenly spread out
     * this will calculate a value between 0.0 < rand < 1.0 multiplied by the full integer range
     */
    override val asListOrdered: List<AudioTrackContext>
        @Synchronized get() {
            if (!isShuffle) {
                return asList
            }

            if (!shouldUpdateShuffledQueue) {
                return cachedShuffledQueue
            }

            val newList = ArrayList<AudioTrackContext>()
            newList.addAll(asList)

            newList.sort()
            val size = newList.size
            for ((i, atc) in newList.withIndex()) {
                val rand = ((i / (size + 1.0) + 1.0 / (size + 1.0)) * Integer.MAX_VALUE).toInt()
                atc.rand = if (atc.isPriority) Integer.MIN_VALUE else rand
            }

            cachedShuffledQueue = newList

            shouldUpdateShuffledQueue = false
            return newList
        }

    override fun skipped() {
        lastTrack = null
    }

    override fun setLastTrack(lastTrack: AudioTrackContext) {
        this.lastTrack = lastTrack
    }

    override fun provideAudioTrack(): AudioTrackContext? {
        if (repeatMode == RepeatMode.SINGLE && lastTrack != null) {
            return lastTrack!!.makeClone()
        }
        if (repeatMode == RepeatMode.ALL && lastTrack != null) {
            //add a fresh copy of the last track back to the queue, if the queue is being repeated
            val clone = lastTrack!!.makeClone()
            clone.isPriority = false
            if (isShuffle) {
                clone.rand = Integer.MAX_VALUE //put it at the back of the shuffled queue
                shouldUpdateShuffledQueue = true
            }
            synchronized(this)
            {
                queue.add(clone)
            }
        }
        if (isShuffle) {
            val list = asListOrdered

            if (list.isEmpty()) {
                return null
            }

            shouldUpdateShuffledQueue = true
            lastTrack = list[0]
            synchronized(this)
            {
                lastTrack?.let{ queue.remove(it) }
            }
            return lastTrack
        } else {
            synchronized(this)
            {
                lastTrack = queue.poll()
            }
            return lastTrack
        }
    }

    override fun remove(atc: AudioTrackContext): Boolean {
        return if (synchronized(this) {queue.remove(atc)}) {
            shouldUpdateShuffledQueue = true
            true
        } else {
            false
        }
    }

    override fun removeAll(tracks: Collection<AudioTrackContext>) {
        if (synchronized(this) {queue.removeAll(tracks) }) {
            shouldUpdateShuffledQueue = true
        }
    }

    override fun removeAllById(trackIds: Collection<Long>) {
        synchronized(this) { queue.removeIf { audioTrackContext -> trackIds.contains(audioTrackContext.trackId) } }
        shouldUpdateShuffledQueue = true
    }

    override fun getTrack(index: Int): AudioTrackContext {
        return asListOrdered[index]
    }

    /**
     * Returns all songs inclusively from one index till the another in a non-bitching way.
     */
    @Suppress("NAME_SHADOWING")
    override fun getTracksInRange(startIndex: Int, endIndex: Int): List<AudioTrackContext> {

        //make sure startIndex <= endIndex
        val startIndex = if (startIndex < endIndex) startIndex else endIndex
        val endIndex = if (startIndex < endIndex) endIndex else startIndex

        //Collect tracks between the two indices
        var i = 0
        val result = ArrayList<AudioTrackContext>()
        for (atc in asListOrdered) {
            if (i in startIndex until endIndex)
                result.add(atc)
            i++
            if (i >= endIndex) break//abort early if we're done
        }

        //trigger shuffle queue update if we found tracks to remove
        if (result.size > 0) shouldUpdateShuffledQueue = true
        return result
    }

    override val asList: List<AudioTrackContext>
        get() = queue.toList()

    @Synchronized
    override fun reshuffle() {
        synchronized(this)
        {
            queue.forEach {
                it.randomize()
                it.isPriority = false
            }
        }
        shouldUpdateShuffledQueue = true
    }

    override val isEmpty: Boolean
        get() = queue.isEmpty()

    override fun size(): Int {
        return queue.size
    }

    override fun add(track: AudioTrackContext) {
        shouldUpdateShuffledQueue = true

        synchronized(this)
        {
            queue.add(findInsertionPoint(track), track)
        }
    }

    private fun findInsertionPoint(track : AudioTrackContext) : Int
    {
        var trackCount = HashMap<Long, Int>()
        val insertionOwner = track.member.user.id
        trackCount[insertionOwner] = 0
        // Include the current playing track
        if (lastTrack != null)
        {
            trackCount[lastTrack!!.member.user.id] = (trackCount[lastTrack!!.member.user.id]?: 0) + 1
        }
        for (insertionIndex in 0 until queue.size)
        {
            val owner = queue[insertionIndex].member.user.id
            trackCount[owner] = (trackCount[owner] ?: 0) + 1
            if (owner != insertionOwner && trackCount[owner] ?: 0 > trackCount[insertionOwner] ?: 0)
            {
                return insertionIndex
            }
        }

        return queue.size
    }

    override fun addAll(tracks: Collection<AudioTrackContext>) {
        shouldUpdateShuffledQueue = true
        synchronized(this)
        {
            queue.addAll(tracks)
        }
    }

    override fun addFirst(track: AudioTrackContext) {
        shouldUpdateShuffledQueue = true
        track.rand = Integer.MIN_VALUE
        synchronized(this)
        {
            queue.addFirst(track)
        }
    }

    override fun addAllFirst(tracks: Collection<AudioTrackContext>) {
        shouldUpdateShuffledQueue = true
        tracks.reversed().forEach {
            it.rand = Integer.MIN_VALUE
            synchronized(this)
            {
                queue.addFirst(it)
            }
        }
    }

    override fun clear() {
        lastTrack = null
        shouldUpdateShuffledQueue = true
        synchronized(this)
        {
            queue.clear()
        }
    }

    override val durationMillis: Long
        get() {
            var duration: Long = 0
            synchronized(this)
            {
                for (atc in queue) {
                    if (!atc.track.info.isStream) {
                        duration += atc.effectiveDuration
                    }
                }
            }
            return duration
        }

    override fun streamsCount(): Int {
        var streams = 0
        synchronized(this)
        {
            for (atc in queue) {
                if (atc.track.info.isStream) {
                    streams++
                }
            }
        }
        return streams
    }

    override fun peek(): AudioTrackContext? {
        return if (isShuffle && queue.size > 0) {
            asListOrdered[0]
        } else {
            queue.peek()
        }
    }

    override fun isUserTrackOwner(userId: Long, trackIds: Collection<Long>): Boolean {
        for (atc in asListOrdered) {
            if (trackIds.contains(atc.trackId) && atc.userId != userId) {
                return false
            }
        }
        return true
    }
}
