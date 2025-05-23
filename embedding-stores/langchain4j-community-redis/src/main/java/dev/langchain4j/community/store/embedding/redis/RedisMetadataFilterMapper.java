package dev.langchain4j.community.store.embedding.redis;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;

/**
 * Metadata Filter Mapper according to <a href="https://redis.io/docs/latest/develop/interact/search-and-query/query/">RedisSearch Document</a>
 */
class RedisMetadataFilterMapper {

    private static final String FILTER_PREFIX = "@";
    private static final String NOT_PREFIX = "-";
    private static final String OR_DELIMITER = " | ";

    private final Map<String, SchemaField> schemaFieldMap;

    RedisMetadataFilterMapper(Map<String, SchemaField> schemaFieldMap) {
        this.schemaFieldMap = schemaFieldMap;
    }

    String mapToFilter(Filter filter) {
        if (filter == null) {
            return "(*)";
        }

        if (filter instanceof IsEqualTo isEqualTo) {
            return mapEqual(isEqualTo);
        } else if (filter instanceof IsNotEqualTo isNotEqualTo) {
            return mapNotEqual(isNotEqualTo);
        } else if (filter instanceof IsGreaterThan isGreaterThan) {
            return mapGreaterThan(isGreaterThan);
        } else if (filter instanceof IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
            return mapGreaterThanOrEqual(isGreaterThanOrEqualTo);
        } else if (filter instanceof IsLessThan isLessThan) {
            return mapLessThan(isLessThan);
        } else if (filter instanceof IsLessThanOrEqualTo isLessThanOrEqualTo) {
            return mapLessThanOrEqual(isLessThanOrEqualTo);
        } else if (filter instanceof IsIn isIn) {
            return mapIn(isIn);
        } else if (filter instanceof IsNotIn isNotIn) {
            return mapNotIn(isNotIn);
        } else if (filter instanceof And and) {
            return mapAnd(and);
        } else if (filter instanceof Not not) {
            return mapNot(not);
        } else if (filter instanceof Or or) {
            return mapOr(or);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    /**
     * <ul>
     *     <li>Numeric: @key:[value]</li>
     *     <li>Tag: @key:{value}</li>
     *     <li>Text: @key:\"value\"</li>
     * </ul>
     */
    String mapEqual(IsEqualTo filter) {
        return doMapEqual(filter.key(), filter.comparisonValue());
    }

    /**
     * <ul>
     *     <li>Numeric: -@key:[value]</li>
     *     <li>Tag: -@key:{value}</li>
     *     <li>Text: -@key:\"value\"</li>
     * </ul>
     */
    String mapNotEqual(IsNotEqualTo filter) {
        return doMapNot(doMapEqual(filter.key(), filter.comparisonValue()));
    }

    /**
     * Only support NumericType
     *
     * <p>Numeric: @key:[(value inf]</p>
     */
    String mapGreaterThan(IsGreaterThan filter) {
        Numeric value = Numeric.constructNumeric(filter.comparisonValue(), true);
        return doMapCompare(filter.key(), value.toString(), Numeric.POSITIVE_INFINITY.toString());
    }

    /**
     * Only support NumericType
     *
     * <p>Numeric: @key:[value inf]</p>
     */
    String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo filter) {
        Numeric value = Numeric.constructNumeric(filter.comparisonValue(), false);
        return doMapCompare(filter.key(), value.toString(), Numeric.POSITIVE_INFINITY.toString());
    }

    /**
     * Only support NumericType
     *
     * <p>Numeric: @key:[-inf (value]</p>
     */
    String mapLessThan(IsLessThan filter) {
        Numeric value = Numeric.constructNumeric(filter.comparisonValue(), true);
        return doMapCompare(filter.key(), Numeric.NEGATIVE_INFINITY.toString(), value.toString());
    }

    /**
     * Only support NumericType
     *
     * <p>Numeric: @key:[-inf value]</p>
     */
    String mapLessThanOrEqual(IsLessThanOrEqualTo filter) {
        Numeric value = Numeric.constructNumeric(filter.comparisonValue(), false);
        return doMapCompare(filter.key(), Numeric.NEGATIVE_INFINITY.toString(), value.toString());
    }

    /**
     * Only support TagType and TextType
     *
     * <ul>
     *     <li>TagType: @key:{value1 | value2 | value3 | ...}</li>
     *     <li>TextType: @key:("value1" | "value2" | "value3" | ...)</li>
     * </ul>
     */
    String mapIn(IsIn filter) {
        return doMapIn(filter.key(), filter.comparisonValues());
    }

    /**
     * Only support TagType and TextType
     *
     * <ul>
     *     <li>TagType: -@key:{value1 | value2 | value3 | ...}</li>
     *     <li>TextType: -@key:("value1" | "value2" | "value3" | ...)</li>
     * </ul>
     */
    String mapNotIn(IsNotIn filter) {
        return doMapNot(doMapIn(filter.key(), filter.comparisonValues()));
    }

    /**
     * (left right)
     */
    String mapAnd(And filter) {
        // @filter1 @filter2
        return "(" + mapToFilter(filter.left()) + " " + mapToFilter(filter.right()) + ")";
    }

    /**
     * -filter
     */
    String mapNot(Not filter) {
        // -@filter
        return doMapNot(mapToFilter(filter.expression()));
    }

    /**
     * (left | right)
     */
    String mapOr(Or filter) {
        // @filter1 | @filter2
        return "(" + mapToFilter(filter.left()) + OR_DELIMITER + mapToFilter(filter.right()) + ")";
    }

    private String doMapEqual(String key, Object value) {
        SchemaField fieldType = schemaFieldMap.getOrDefault(key, TagField.of(key));

        String keyPrefix = toKeyPrefix(key);
        if (fieldType instanceof NumericField) {
            return keyPrefix + Boundary.NUMERIC_BOUNDARY.toSingleString(value);
        } else if (fieldType instanceof TagField) {
            return keyPrefix + Boundary.TAG_BOUNDARY.toSingleString(value);
        } else if (fieldType instanceof TextField) {
            return keyPrefix + Boundary.TEXT_BOUNDARY.toSingleString(value);
        } else {
            throw new UnsupportedOperationException("Unsupported field type: " + fieldType);
        }
    }

    private String doMapCompare(String key, String leftValue, String rightValue) {
        SchemaField fieldType = schemaFieldMap.getOrDefault(key, TextField.of(key));

        if (fieldType instanceof NumericField) {
            return toKeyPrefix(key) + Boundary.NUMERIC_BOUNDARY.toRangeString(leftValue, rightValue);
        } else {
            throw new UnsupportedOperationException(
                    "Redis do not support non-Numeric range search, fieldType: " + fieldType);
        }
    }

    private String doMapIn(String key, Collection<?> values) {
        SchemaField fieldType = schemaFieldMap.getOrDefault(key, TagField.of(key));

        String keyPrefix = toKeyPrefix(key);
        if (fieldType instanceof TagField) {
            String inFilter = values.stream().map(Object::toString).collect(Collectors.joining(OR_DELIMITER));

            return keyPrefix + Boundary.TAG_BOUNDARY.toSingleString(inFilter);
        } else if (fieldType instanceof TextField) {
            String inFilter = values.stream()
                    .map(Boundary.TEXT_BOUNDARY::toSingleString)
                    .collect(Collectors.joining(OR_DELIMITER));

            return keyPrefix + Boundary.TEXT_IN_BOUNDARY.toSingleString(inFilter);
        } else {
            throw new UnsupportedOperationException(
                    "Redis do not support NumericType \"in\" search, fieldType: " + fieldType);
        }
    }

    private String doMapNot(String filter) {
        return String.format("(%s%s)", NOT_PREFIX, filter);
    }

    private String toKeyPrefix(String key) {
        return FILTER_PREFIX + key + ":";
    }

    static class Numeric {

        static final Numeric POSITIVE_INFINITY = new Numeric(Double.POSITIVE_INFINITY, true);
        static final Numeric NEGATIVE_INFINITY = new Numeric(Double.NEGATIVE_INFINITY, true);

        private static final String INFINITY = "inf";
        private static final String MINUS_INFINITY = "-inf";
        private static final String INCLUSIVE_FORMAT = "%s";
        private static final String EXCLUSIVE_FORMAT = "(%s";

        private final Object value;
        private final boolean exclusive;

        Numeric(Object value, boolean exclusive) {
            this.value = value;
            this.exclusive = exclusive;
        }

        static Numeric constructNumeric(Object value, boolean exclusive) {
            return new Numeric(value, exclusive);
        }

        @Override
        public String toString() {
            if (this == POSITIVE_INFINITY) {
                return INFINITY;
            } else if (this == NEGATIVE_INFINITY) {
                return MINUS_INFINITY;
            }

            return String.format(formatString(), value);
        }

        private String formatString() {
            if (exclusive) {
                return EXCLUSIVE_FORMAT;
            }
            return INCLUSIVE_FORMAT;
        }
    }

    static class Boundary {

        static final Boundary TAG_BOUNDARY = new Boundary("{", "}");
        static final Boundary TEXT_BOUNDARY = new Boundary("\"", "\"");
        static final Boundary TEXT_IN_BOUNDARY = new Boundary("(", ")");
        static final Boundary NUMERIC_BOUNDARY = new Boundary("[", "]");

        private final String left;
        private final String right;

        Boundary(String left, String right) {
            this.left = left;
            this.right = right;
        }

        String toSingleString(Object value) {
            return String.format("%s%s%s", left, value, right);
        }

        String toRangeString(Object leftValue, Object rightValue) {
            return String.format("%s%s %s%s", left, leftValue, rightValue, right);
        }
    }
}
