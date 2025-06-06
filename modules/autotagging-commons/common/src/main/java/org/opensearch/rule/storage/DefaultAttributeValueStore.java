/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.rule.storage;

import org.apache.commons.collections4.trie.PatriciaTrie;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This is a patricia trie based implementation of AttributeValueStore
 * We are choosing patricia trie because it provides very fast search operations on prefix matches as well as range
 * lookups. It provides a very efficient storage for strings
 * ref: https://commons.apache.org/proper/commons-collections/javadocs/api-4.4/org/apache/commons/collections4/trie/PatriciaTrie.html
 */
public class DefaultAttributeValueStore<K extends String, V> implements AttributeValueStore<K, V> {
    private final PatriciaTrie<V> trie;
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private static final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    /**
     * Default constructor
     */
    public DefaultAttributeValueStore() {
        this(new PatriciaTrie<>());
    }

    /**
     * Main constructor
     * @param trie A Patricia Trie
     */
    public DefaultAttributeValueStore(PatriciaTrie<V> trie) {
        this.trie = trie;
    }

    @Override
    public void put(K key, V value) {
        writeLock.lock();
        try {
            trie.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void remove(String key) {
        writeLock.lock();
        try {
            trie.remove(key);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Optional<V> get(String key) {
        readLock.lock();
        try {
            /**
             * Since we are inserting prefixes into the trie and searching for larger strings
             * It is important to find the largest matching prefix key in the trie efficiently
             * Hence we can do binary search
             */
            final String longestMatchingPrefix = findLongestMatchingPrefix(key);

            /**
             * Now there are following cases for this prefix
             * 1. There is a Rule which has this prefix as one of the attribute values. In this case we should return the
             *     Rule's label otherwise send empty
             */
            for (Map.Entry<String, V> possibleMatch : trie.prefixMap(longestMatchingPrefix).entrySet()) {
                if (key.startsWith(possibleMatch.getKey())) {
                    return Optional.of(possibleMatch.getValue());
                }
            }
        } finally {
            readLock.unlock();
        }
        return Optional.empty();
    }

    private String findLongestMatchingPrefix(String key) {
        int low = 0;
        int high = key.length() - 1;

        while (low < high) {
            int mid = (high + low + 1) / 2;
            /**
             * This operation has O(1) complexity because prefixMap returns only the iterator
             */
            if (!trie.prefixMap(key.substring(0, mid)).isEmpty()) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        return key.substring(0, low);
    }

    @Override
    public void clear() {
        writeLock.lock();
        try {
            trie.clear();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public int size() {
        return trie.size();
    }
}
