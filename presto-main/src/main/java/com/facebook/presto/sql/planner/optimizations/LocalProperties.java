/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.sql.planner.optimizations;

import com.facebook.presto.spi.GroupingProperty;
import com.facebook.presto.spi.LocalProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.PeekingIterator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.collect.Iterators.peekingIterator;

final class LocalProperties
{
    private LocalProperties()
    {
    }

    public static <T> List<LocalProperty<T>> none()
    {
        return ImmutableList.of();
    }

    public static <T> List<LocalProperty<T>> grouped(Collection<T> columns)
    {
        return ImmutableList.of(new GroupingProperty<>(columns));
    }

    /**
     * Attempt to match the desired properties to a sequence of known properties.
     * <p>
     * Returns a list of the same length as the original. Entries are:
     * - Optional.empty(): the property was satisfied completely
     * - non-empty: the (simplified) property that was not satisfied
     */
    public static <T> List<Optional<LocalProperty<T>>> match(List<LocalProperty<T>> actuals, List<LocalProperty<T>> desired)
    {
        // After normalizing actuals, each symbol should only appear once
        PeekingIterator<LocalProperty<T>> actualIterator = peekingIterator(normalizeAndPrune(actuals).iterator());

        Set<T> constants = new HashSet<>();
        boolean consumeMoreActuals = true;
        List<Optional<LocalProperty<T>>> result = new ArrayList<>(desired.size());
        for (LocalProperty<T> desiredProperty : desired) {
            while (consumeMoreActuals && actualIterator.hasNext() && desiredProperty.isSimplifiedBy(actualIterator.peek())) {
                constants.addAll(actualIterator.next().getColumns());
            }
            Optional<LocalProperty<T>> simplifiedDesired = desiredProperty.withConstants(constants);
            consumeMoreActuals &= !simplifiedDesired.isPresent(); // Only continue processing actuals if all previous desired properties were fully satisfied
            result.add(simplifiedDesired);
        }
        return result;
    }

    /**
     * Normalizes the local properties and potentially consolidates it to the smallest possible list
     * NOTE: When normalized, each symbol will only appear once
     */
    public static <T> List<LocalProperty<T>> normalizeAndPrune(List<? extends LocalProperty<T>> localProperties)
    {
        return normalize(localProperties).stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Normalizes the local properties by removing redundant symbols, but retains the original local property positions
     */
    public static <T> List<Optional<LocalProperty<T>>> normalize(List<? extends LocalProperty<T>> localProperties)
    {
        List<Optional<LocalProperty<T>>> normalizedProperties = new ArrayList<>(localProperties.size());
        Set<T> constants = new HashSet<>();
        for (LocalProperty<T> localProperty : localProperties) {
            normalizedProperties.add(localProperty.withConstants(constants));
            constants.addAll(localProperty.getColumns());
        }
        return normalizedProperties;
    }
}
