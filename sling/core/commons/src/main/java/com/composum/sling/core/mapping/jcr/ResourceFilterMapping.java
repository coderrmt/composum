package com.composum.sling.core.mapping.jcr;

import com.composum.sling.core.ResourceHandle;
import com.composum.sling.core.filter.ResourceFilter;
import com.composum.sling.core.filter.StringFilter;
import com.composum.sling.core.util.ResourceUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rw on 19.05.15.
 */
public class ResourceFilterMapping {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceFilterMapping.class);

    public static final String PROPERTY_TYPE = "type";
    public static final String PROPERTY_RULE = "rule";
    public static final String NODE_NAME_FILTER = "filter";
    public static final String NODE_NAME_ENTRY = "entry";
    public static final String RESOURCE_FILTER_TYPE = "composum/nodes/core/filter/resources";

    //
    // String mapping (useful for OSGi configuration properties)
    //

    public static final Pattern FILTER_SET_PATTERN = Pattern.compile(
            "^(and|or|first|last|tree)\\{(.+)\\}$"
    );
    public static final Pattern STRING_PATTERN = Pattern.compile(
            "^(Name|Path|PrimaryType|MixinType|ResourceType|MimeType|All|Folder)\\((.*)\\)$"
    );
    public static final String DEFAULT_FILTER_TYPE = "Path";

    public static ResourceFilter fromString(String rules) {
        ResourceFilter filter = ResourceFilter.ALL;
        Matcher matcher = FILTER_SET_PATTERN.matcher(rules);
        if (matcher.matches()) {
            String type = matcher.group(1);
            String values = matcher.group(2);
            try {
                ResourceFilter.FilterSet.Rule rule = ResourceFilter.FilterSet.Rule.valueOf(type);
                List<ResourceFilter> filters = new ArrayList<>();
                String nextRule = "";
                for (String item : StringUtils.split(values, ',')) {
                    nextRule += item;
                    if (StringUtils.isBlank(nextRule) ||
                            STRING_PATTERN.matcher(nextRule).matches() ||
                            FILTER_SET_PATTERN.matcher(nextRule).matches()) {
                        filters.add(fromString(nextRule));
                        nextRule = "";
                    } else {
                        nextRule += ",";
                    }
                }
                filter = new ResourceFilter.FilterSet(rule, filters);
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        } else {
            matcher = STRING_PATTERN.matcher(rules);
            if (matcher.matches()) {
                String type = matcher.group(1);
                if (StringUtils.isBlank(type)) {
                    type = DEFAULT_FILTER_TYPE;
                }
                String values = matcher.group(2);
                try {
                    StringFilter stringFilter = StringFilterMapping.fromString(values);
                    Class<?> filterClass = Class.forName(
                            ResourceFilter.class.getName() + "$" + type + "Filter");
                    if (ResourceFilter.AllFilter.class.equals(filterClass)) {
                        filter = ResourceFilter.ALL;
                    } else if (ResourceFilter.FolderFilter.class.equals(filterClass)) {
                        filter = ResourceFilter.FOLDER;
                    } else {
                        filter = (ResourceFilter) filterClass
                                .getConstructor(StringFilter.class)
                                .newInstance(stringFilter);
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            } else {
                if (StringUtils.isNotBlank(rules)) {
                    LOG.error("invalid filter rule: '" + rules + "'");
                }
            }
        }
        return filter;
    }

    public static String toString(ResourceFilter filter) {
        StringBuilder builder = new StringBuilder();
        filter.toString(builder);
        return builder.toString();
    }

    //
    // general entry point
    //

    public static ResourceFilter fromResource(Resource resource) throws Exception {
        ResourceFilter filter = null;
        if (resource != null) {
            ResourceHandle handle = ResourceHandle.use(resource);
            String typeName = handle.getProperty(PROPERTY_TYPE);
            Class<? extends ResourceFilter> type = getType(typeName);
            MappingStrategy strategy = getStrategy(type);
            filter = strategy.fromResource(resource);
        }
        return filter;
    }

    public static void toResource(Resource resource, ResourceFilter filter) throws RepositoryException {
        if (resource != null) {
            MappingStrategy strategy = getStrategy(filter.getClass());
            strategy.toResource(resource, filter);
        }
    }

    //
    // strategy implementations
    //

    protected static final Map<Class<? extends ResourceFilter>, MappingStrategy> STRATEGY_MAP;

    static {
        STRATEGY_MAP = new HashMap<>();
        STRATEGY_MAP.put(ResourceFilter.FilterSet.class, new FilterSetStrategy());
        STRATEGY_MAP.put(ResourceFilter.PrimaryTypeFilter.class, new PatternFilterStrategy());
        STRATEGY_MAP.put(ResourceFilter.MixinTypeFilter.class, new PatternFilterStrategy());
        STRATEGY_MAP.put(ResourceFilter.MimeTypeFilter.class, new PatternFilterStrategy());
        STRATEGY_MAP.put(ResourceFilter.ResourceTypeFilter.class, new PatternFilterStrategy());
        STRATEGY_MAP.put(ResourceFilter.PathFilter.class, new PatternFilterStrategy());
        STRATEGY_MAP.put(ResourceFilter.NameFilter.class, new PatternFilterStrategy());
        STRATEGY_MAP.put(ResourceFilter.FolderFilter.class, new PredefinedFilterStrategy(ResourceFilter.FOLDER));
        STRATEGY_MAP.put(ResourceFilter.AllFilter.class, new PredefinedFilterStrategy(ResourceFilter.ALL));
    }

    protected static final MappingStrategy DEFAULT_STRATEGY = new GeneralStrategy();

    public interface MappingStrategy {

        ResourceFilter fromResource(Resource resource) throws Exception;

        void toResource(Resource resource, ResourceFilter filter) throws RepositoryException;
    }

    public static class GeneralStrategy implements MappingStrategy {

        protected ResourceFilter createInstance(ResourceHandle resource,
                                                Class<? extends ResourceFilter> type)
                throws Exception {
            ResourceFilter filter = type.newInstance();
            return filter;
        }

        @Override
        public ResourceFilter fromResource(Resource resource) throws Exception {
            ResourceHandle handle = ResourceHandle.use(resource);
            String typeName = handle.getProperty(PROPERTY_TYPE, (String) null);
            Class<? extends ResourceFilter> type = getType(typeName);
            ResourceFilter filter = createInstance(handle, type);
            return filter;
        }

        @Override
        public void toResource(Resource resource, ResourceFilter filter) throws RepositoryException {
            ResourceHandle handle = ResourceHandle.use(resource);
            handle.setProperty(ResourceUtil.PROP_RESOURCE_TYPE, RESOURCE_FILTER_TYPE);
            handle.setProperty(PROPERTY_TYPE, getTypeName(filter));
        }
    }

    public static class PredefinedFilterStrategy extends GeneralStrategy {

        private final ResourceFilter instance;

        public PredefinedFilterStrategy(ResourceFilter instance) {
            this.instance = instance;
        }

        @Override
        protected ResourceFilter createInstance(ResourceHandle resource,
                                                Class<? extends ResourceFilter> type) throws Exception {
            return this.instance;
        }
    }

    public static class PatternFilterStrategy extends GeneralStrategy {

        @Override
        protected ResourceFilter createInstance(ResourceHandle resource,
                                                Class<? extends ResourceFilter> type)
                throws Exception {
            Resource stringFilterRes = resource.getChild(NODE_NAME_FILTER);
            StringFilter stringFilter = StringFilterMapping.fromResource(stringFilterRes);
            ResourceFilter filter = type.getConstructor(StringFilter.class).newInstance(stringFilter);
            return filter;
        }

        @Override
        public void toResource(Resource resource, ResourceFilter filter) throws RepositoryException {
            super.toResource(resource, filter);
            StringFilter stringFilter = ((ResourceFilter.PatternFilter) filter).getFilter();
            Resource stringFilterRes = ResourceUtil.getOrCreateChild(resource,
                    NODE_NAME_FILTER, ResourceUtil.TYPE_UNSTRUCTURED);
            StringFilterMapping.toResource(stringFilterRes, stringFilter);
        }
    }

    public static class FilterSetStrategy extends GeneralStrategy {

        @Override
        protected ResourceFilter createInstance(ResourceHandle resource,
                                                Class<? extends ResourceFilter> type)
                throws Exception {
            ResourceFilter.FilterSet.Rule rule = ResourceFilter.FilterSet.Rule.valueOf(
                    resource.getProperty(PROPERTY_RULE, (String) null));
            List<ResourceHandle> filterResources = resource.getChildrenByResourceType(RESOURCE_FILTER_TYPE);
            List<ResourceFilter> filterList = new ArrayList<>();
            for (ResourceHandle filterRes : filterResources) {
                ResourceFilter filter = ResourceFilterMapping.fromResource(filterRes);
                filterList.add(filter);
            }
            ResourceFilter filter = type.getConstructor(
                    ResourceFilter.FilterSet.Rule.class, List.class)
                    .newInstance(rule, filterList);
            return filter;
        }

        @Override
        public void toResource(Resource resource, ResourceFilter filter) throws RepositoryException {
            super.toResource(resource, filter);
            ResourceFilter.FilterSet filterSet = (ResourceFilter.FilterSet) filter;
            ResourceHandle handle = ResourceHandle.use(resource);
            ResourceFilter.FilterSet.Rule rule = filterSet.getRule();
            handle.setProperty(PROPERTY_RULE, rule.name());
            List<ResourceFilter> set = filterSet.getSet();
            ResourceHandle entry;
            for (int i = 0; i < set.size(); i++) {
                entry = ResourceHandle.use(ResourceUtil.getOrCreateChild(resource,
                        NODE_NAME_ENTRY + "-" + i, ResourceUtil.TYPE_UNSTRUCTURED));
                ResourceFilterMapping.toResource(entry, set.get(i));
            }
        }
    }

    //
    // type mapping
    //

    public static MappingStrategy getStrategy(Class<? extends ResourceFilter> type) {
        MappingStrategy strategy = STRATEGY_MAP.get(type);
        return strategy != null ? strategy : DEFAULT_STRATEGY;
    }

    public static final Pattern SIMPLIFY_TYPE_PATTERN =
            Pattern.compile("^" + ResourceFilter.class.getName() + ".([A-Za-z]+)$");
    public static final Pattern IS_SIMPLIFIED_TYPE_PATTERN = Pattern.compile("^[A-Za-z]+$");

    public static Class<? extends ResourceFilter> getType(String typeName) throws Exception {
        Class<? extends ResourceFilter> type;
        if (ResourceFilterMapping.IS_SIMPLIFIED_TYPE_PATTERN.matcher(typeName).matches()) {
            typeName = ResourceFilter.class.getName() + "$" + typeName;
        }
        try {
            type = (Class<ResourceFilter>) Class.forName(typeName);
        } catch (ClassNotFoundException cnfex) {
            type = (Class<ResourceFilter>) Class.forName(typeName + "Filter");
        }
        return type;
    }

    public static String getTypeName(ResourceFilter value) {
        String typeName = value.getClass().getName();
        Matcher simplifyTypeMatcher = ResourceFilterMapping.SIMPLIFY_TYPE_PATTERN.matcher(typeName);
        if (simplifyTypeMatcher.matches()) {
            typeName = simplifyTypeMatcher.group(1);
        }
        return typeName;
    }
}
