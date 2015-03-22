/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2012 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gson.internal;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.Set;


/**
 * A map of comparable keys to values. Unlike {@code TreeMap}, this class uses
 * insertion order for iteration order. Comparison order is only used as an
 * optimization for efficient insertion and removal.
 *
 * <p>This implementation was derived from Android 4.1's TreeMap class.
 */
public final class LinkedTreeMap<K, V> extends AbstractMap<K, V> implements Serializable {

    @SuppressWarnings({"unchecked", "rawtypes"})
    // to avoid Comparable<Comparable<Comparable<...>>>
    private static final Comparator<Comparable> NATURAL_ORDER = new Comparator<Comparable>() {

                                                                  public int compare(final Comparable a, final Comparable b) {
                                                                      return a.compareTo(b);
                                                                  }
                                                              };

    Comparator<? super K>                       comparator;
    Node<K, V>                                  root;
    int                                         size          = 0;
    int                                         modCount      = 0;

    // Used to preserve iteration order
    final Node<K, V>                            header        = new Node<K, V>();

    /**
     * Create a natural order, empty tree map whose keys must be mutually
     * comparable and non-null.
     */
    @SuppressWarnings("unchecked")
    // unsafe! this assumes K is comparable
    public LinkedTreeMap() {
        this((Comparator<? super K>) NATURAL_ORDER);
    }

    /**
     * Create a tree map ordered by {@code comparator}. This map's keys may only
     * be null if {@code comparator} permits.
     *
     * @param comparator the comparator to order elements with, or {@code null} to
     *     use the natural ordering.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    // unsafe! if comparator is null, this assumes K is comparable
    public LinkedTreeMap(final Comparator<? super K> comparator) {
        this.comparator = comparator != null
                ? comparator
                : (Comparator) NATURAL_ORDER;
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public V get(final Object key) {
        final Node<K, V> node = this.findByObject(key);
        return node != null ? node.value : null;
    }

    @Override
    public boolean containsKey(final Object key) {
        return this.findByObject(key) != null;
    }

    @Override
    public V put(final K key, final V value) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }
        final Node<K, V> created = this.find(key, true);
        final V result = created.value;
        created.value = value;
        return result;
    }

    @Override
    public void clear() {
        this.root = null;
        this.size = 0;
        this.modCount++;

        // Clear iteration order
        final Node<K, V> header = this.header;
        header.next = header.prev = header;
    }

    @Override
    public V remove(final Object key) {
        final Node<K, V> node = this.removeInternalByKey(key);
        return node != null ? node.value : null;
    }

    /**
     * Returns the node at or adjacent to the given key, creating it if requested.
     *
     * @throws ClassCastException if {@code key} and the tree's keys aren't
     *     mutually comparable.
     */
    Node<K, V> find(final K key, final boolean create) {
        final Comparator<? super K> comparator = this.comparator;
        Node<K, V> nearest = this.root;
        int comparison = 0;

        if (nearest != null) {
            // Micro-optimization: avoid polymorphic calls to Comparator.compare().
            @SuppressWarnings("unchecked")
            final// Throws a ClassCastException below if there's trouble.
            Comparable<Object> comparableKey = (comparator == NATURAL_ORDER)
                    ? (Comparable<Object>) key
                    : null;

            while (true) {
                comparison = (comparableKey != null)
                        ? comparableKey.compareTo(nearest.key)
                        : comparator.compare(key, nearest.key);

                // We found the requested key.
                if (comparison == 0) {
                    return nearest;
                }

                // If it exists, the key is in a subtree. Go deeper.
                final Node<K, V> child = (comparison < 0) ? nearest.left : nearest.right;
                if (child == null) {
                    break;
                }

                nearest = child;
            }
        }

        // The key doesn't exist in this tree.
        if (!create) {
            return null;
        }

        // Create the node and add it to the tree or the table.
        final Node<K, V> header = this.header;
        Node<K, V> created;
        if (nearest == null) {
            // Check that the value is comparable if we didn't do any comparisons.
            if (comparator == NATURAL_ORDER && !(key instanceof Comparable)) {
                throw new ClassCastException(key.getClass().getName() + " is not Comparable");
            }
            created = new Node<K, V>(nearest, key, header, header.prev);
            this.root = created;
        } else {
            created = new Node<K, V>(nearest, key, header, header.prev);
            if (comparison < 0) { // nearest.key is higher
                nearest.left = created;
            } else { // comparison > 0, nearest.key is lower
                nearest.right = created;
            }
            this.rebalance(nearest, true);
        }
        this.size++;
        this.modCount++;

        return created;
    }

    @SuppressWarnings("unchecked")
    Node<K, V> findByObject(final Object key) {
        try {
            return key != null ? this.find((K) key, false) : null;
        } catch (final ClassCastException e) {
            return null;
        }
    }

    /**
     * Returns this map's entry that has the same key and value as {@code
     * entry}, or null if this map has no such entry.
     *
     * <p>This method uses the comparator for key equality rather than {@code
     * equals}. If this map's comparator isn't consistent with equals (such as
     * {@code String.CASE_INSENSITIVE_ORDER}), then {@code remove()} and {@code
     * contains()} will violate the collections API.
     */
    Node<K, V> findByEntry(final Entry<?, ?> entry) {
        final Node<K, V> mine = this.findByObject(entry.getKey());
        final boolean valuesEqual = mine != null && this.equal(mine.value, entry.getValue());
        return valuesEqual ? mine : null;
    }

    private boolean equal(final Object a, final Object b) {
        return a == b || (a != null && a.equals(b));
    }

    /**
     * Removes {@code node} from this tree, rearranging the tree's structure as
     * necessary.
     *
     * @param unlink true to also unlink this node from the iteration linked list.
     */
    void removeInternal(final Node<K, V> node, final boolean unlink) {
        if (unlink) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
        }

        Node<K, V> left = node.left;
        Node<K, V> right = node.right;
        final Node<K, V> originalParent = node.parent;
        if (left != null && right != null) {

            /*
             * To remove a node with both left and right subtrees, move an
             * adjacent node from one of those subtrees into this node's place.
             * 
             * Removing the adjacent node may change this node's subtrees. This
             * node may no longer have two subtrees once the adjacent node is
             * gone!
             */

            final Node<K, V> adjacent = (left.height > right.height) ? left.last() : right.first();
            this.removeInternal(adjacent, false); // takes care of rebalance and size--

            int leftHeight = 0;
            left = node.left;
            if (left != null) {
                leftHeight = left.height;
                adjacent.left = left;
                left.parent = adjacent;
                node.left = null;
            }

            int rightHeight = 0;
            right = node.right;
            if (right != null) {
                rightHeight = right.height;
                adjacent.right = right;
                right.parent = adjacent;
                node.right = null;
            }

            adjacent.height = Math.max(leftHeight, rightHeight) + 1;
            this.replaceInParent(node, adjacent);
            return;
        } else if (left != null) {
            this.replaceInParent(node, left);
            node.left = null;
        } else if (right != null) {
            this.replaceInParent(node, right);
            node.right = null;
        } else {
            this.replaceInParent(node, null);
        }

        this.rebalance(originalParent, false);
        this.size--;
        this.modCount++;
    }

    Node<K, V> removeInternalByKey(final Object key) {
        final Node<K, V> node = this.findByObject(key);
        if (node != null) {
            this.removeInternal(node, true);
        }
        return node;
    }

    private void replaceInParent(final Node<K, V> node, final Node<K, V> replacement) {
        final Node<K, V> parent = node.parent;
        node.parent = null;
        if (replacement != null) {
            replacement.parent = parent;
        }

        if (parent != null) {
            if (parent.left == node) {
                parent.left = replacement;
            } else {
                assert (parent.right == node);
                parent.right = replacement;
            }
        } else {
            this.root = replacement;
        }
    }

    /**
     * Rebalances the tree by making any AVL rotations necessary between the
     * newly-unbalanced node and the tree's root.
     *
     * @param insert true if the node was unbalanced by an insert; false if it
     *     was by a removal.
     */
    private void rebalance(final Node<K, V> unbalanced, final boolean insert) {
        for (Node<K, V> node = unbalanced; node != null; node = node.parent) {
            final Node<K, V> left = node.left;
            final Node<K, V> right = node.right;
            final int leftHeight = left != null ? left.height : 0;
            final int rightHeight = right != null ? right.height : 0;

            final int delta = leftHeight - rightHeight;
            if (delta == -2) {
                final Node<K, V> rightLeft = right.left;
                final Node<K, V> rightRight = right.right;
                final int rightRightHeight = rightRight != null ? rightRight.height : 0;
                final int rightLeftHeight = rightLeft != null ? rightLeft.height : 0;

                final int rightDelta = rightLeftHeight - rightRightHeight;
                if (rightDelta == -1 || (rightDelta == 0 && !insert)) {
                    this.rotateLeft(node); // AVL right right
                } else {
                    assert (rightDelta == 1);
                    this.rotateRight(right); // AVL right left
                    this.rotateLeft(node);
                }
                if (insert) {
                    break; // no further rotations will be necessary
                }

            } else if (delta == 2) {
                final Node<K, V> leftLeft = left.left;
                final Node<K, V> leftRight = left.right;
                final int leftRightHeight = leftRight != null ? leftRight.height : 0;
                final int leftLeftHeight = leftLeft != null ? leftLeft.height : 0;

                final int leftDelta = leftLeftHeight - leftRightHeight;
                if (leftDelta == 1 || (leftDelta == 0 && !insert)) {
                    this.rotateRight(node); // AVL left left
                } else {
                    assert (leftDelta == -1);
                    this.rotateLeft(left); // AVL left right
                    this.rotateRight(node);
                }
                if (insert) {
                    break; // no further rotations will be necessary
                }

            } else if (delta == 0) {
                node.height = leftHeight + 1; // leftHeight == rightHeight
                if (insert) {
                    break; // the insert caused balance, so rebalancing is done!
                }

            } else {
                assert (delta == -1 || delta == 1);
                node.height = Math.max(leftHeight, rightHeight) + 1;
                if (!insert) {
                    break; // the height hasn't changed, so rebalancing is done!
                }
            }
        }
    }

    /**
     * Rotates the subtree so that its root's right child is the new root.
     */
    private void rotateLeft(final Node<K, V> root) {
        final Node<K, V> left = root.left;
        final Node<K, V> pivot = root.right;
        final Node<K, V> pivotLeft = pivot.left;
        final Node<K, V> pivotRight = pivot.right;

        // move the pivot's left child to the root's right
        root.right = pivotLeft;
        if (pivotLeft != null) {
            pivotLeft.parent = root;
        }

        this.replaceInParent(root, pivot);

        // move the root to the pivot's left
        pivot.left = root;
        root.parent = pivot;

        // fix heights
        root.height = Math.max(left != null ? left.height : 0,
                pivotLeft != null ? pivotLeft.height : 0) + 1;
        pivot.height = Math.max(root.height,
                pivotRight != null ? pivotRight.height : 0) + 1;
    }

    /**
     * Rotates the subtree so that its root's left child is the new root.
     */
    private void rotateRight(final Node<K, V> root) {
        final Node<K, V> pivot = root.left;
        final Node<K, V> right = root.right;
        final Node<K, V> pivotLeft = pivot.left;
        final Node<K, V> pivotRight = pivot.right;

        // move the pivot's right child to the root's left
        root.left = pivotRight;
        if (pivotRight != null) {
            pivotRight.parent = root;
        }

        this.replaceInParent(root, pivot);

        // move the root to the pivot's right
        pivot.right = root;
        root.parent = pivot;

        // fixup heights
        root.height = Math.max(right != null ? right.height : 0,
                pivotRight != null ? pivotRight.height : 0) + 1;
        pivot.height = Math.max(root.height,
                pivotLeft != null ? pivotLeft.height : 0) + 1;
    }

    private EntrySet entrySet;
    private KeySet   keySet;

    @Override
    public Set<Entry<K, V>> entrySet() {
        final EntrySet result = this.entrySet;
        return result != null ? result : (this.entrySet = new EntrySet());
    }

    @Override
    public Set<K> keySet() {
        final KeySet result = this.keySet;
        return result != null ? result : (this.keySet = new KeySet());
    }

    static final class Node<K, V> implements Entry<K, V> {

        Node<K, V> parent;
        Node<K, V> left;
        Node<K, V> right;
        Node<K, V> next;
        Node<K, V> prev;
        final K    key;
        V          value;
        int        height;

        /** Create the header entry */
        Node() {
            this.key = null;
            this.next = this.prev = this;
        }

        /** Create a regular entry */
        Node(final Node<K, V> parent, final K key, final Node<K, V> next, final Node<K, V> prev) {
            this.parent = parent;
            this.key = key;
            this.height = 1;
            this.next = next;
            this.prev = prev;
            prev.next = this;
            next.prev = this;
        }

        public K getKey() {
            return this.key;
        }

        public V getValue() {
            return this.value;
        }

        public V setValue(final V value) {
            final V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean equals(final Object o) {
            if (o instanceof Entry) {
                final Entry other = (Entry) o;
                return (this.key == null ? other.getKey() == null : this.key.equals(other.getKey()))
                        && (this.value == null ? other.getValue() == null : this.value.equals(other.getValue()));
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (this.key == null ? 0 : this.key.hashCode())
                    ^ (this.value == null ? 0 : this.value.hashCode());
        }

        @Override
        public String toString() {
            return this.key + "=" + this.value;
        }

        /**
         * Returns the first node in this subtree.
         */
        public Node<K, V> first() {
            Node<K, V> node = this;
            Node<K, V> child = node.left;
            while (child != null) {
                node = child;
                child = node.left;
            }
            return node;
        }

        /**
         * Returns the last node in this subtree.
         */
        public Node<K, V> last() {
            Node<K, V> node = this;
            Node<K, V> child = node.right;
            while (child != null) {
                node = child;
                child = node.right;
            }
            return node;
        }
    }

    private abstract class LinkedTreeMapIterator<T> implements Iterator<T> {

        Node<K, V> next             = LinkedTreeMap.this.header.next;
        Node<K, V> lastReturned     = null;
        int        expectedModCount = LinkedTreeMap.this.modCount;

        public final boolean hasNext() {
            return this.next != LinkedTreeMap.this.header;
        }

        final Node<K, V> nextNode() {
            final Node<K, V> e = this.next;
            if (e == LinkedTreeMap.this.header) {
                throw new NoSuchElementException();
            }
            if (LinkedTreeMap.this.modCount != this.expectedModCount) {
                throw new ConcurrentModificationException();
            }
            this.next = e.next;
            return this.lastReturned = e;
        }

        public final void remove() {
            if (this.lastReturned == null) {
                throw new IllegalStateException();
            }
            LinkedTreeMap.this.removeInternal(this.lastReturned, true);
            this.lastReturned = null;
            this.expectedModCount = LinkedTreeMap.this.modCount;
        }
    }

    class EntrySet extends AbstractSet<Entry<K, V>> {

        @Override
        public int size() {
            return LinkedTreeMap.this.size;
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new LinkedTreeMapIterator<Entry<K, V>>() {

                public Entry<K, V> next() {
                    return this.nextNode();
                }
            };
        }

        @Override
        public boolean contains(final Object o) {
            return o instanceof Entry && LinkedTreeMap.this.findByEntry((Entry<?, ?>) o) != null;
        }

        @Override
        public boolean remove(final Object o) {
            if (!(o instanceof Entry)) {
                return false;
            }

            final Node<K, V> node = LinkedTreeMap.this.findByEntry((Entry<?, ?>) o);
            if (node == null) {
                return false;
            }
            LinkedTreeMap.this.removeInternal(node, true);
            return true;
        }

        @Override
        public void clear() {
            LinkedTreeMap.this.clear();
        }
    }

    final class KeySet extends AbstractSet<K> {

        @Override
        public int size() {
            return LinkedTreeMap.this.size;
        }

        @Override
        public Iterator<K> iterator() {
            return new LinkedTreeMapIterator<K>() {

                public K next() {
                    return this.nextNode().key;
                }
            };
        }

        @Override
        public boolean contains(final Object o) {
            return LinkedTreeMap.this.containsKey(o);
        }

        @Override
        public boolean remove(final Object key) {
            return LinkedTreeMap.this.removeInternalByKey(key) != null;
        }

        @Override
        public void clear() {
            LinkedTreeMap.this.clear();
        }
    }

    /**
     * If somebody is unlucky enough to have to serialize one of these, serialize
     * it as a LinkedHashMap so that they won't need Gson on the other side to
     * deserialize it. Using serialization defeats our DoS defence, so most apps
     * shouldn't use it.
     */
    private Object writeReplace() throws ObjectStreamException {
        return new LinkedHashMap<K, V>(this);
    }
}