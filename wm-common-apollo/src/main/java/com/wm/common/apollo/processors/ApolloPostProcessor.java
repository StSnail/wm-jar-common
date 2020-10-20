package com.wm.common.apollo.processors;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigChangeListener;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.wm.common.apollo.Apollo;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * apollo注解自动注入
 * Created by Mengwei on 2019/7/19.
 */
public class ApolloPostProcessor implements BeanPostProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ApolloPostProcessor.class);

    private static final Gson gson = new Gson();

    private static final Map<ApolloConfigItem, List<ApolloField>> itemFieldMap = Maps.newConcurrentMap();
    private String project;

    public ApolloPostProcessor(String project) {
        this.project = project;
    }

    private Config getConfig(String namespace) {
        return ConfigService.getConfig(getFullNamespace(namespace));
    }

    private String getFullNamespace(String shortNamespace) {
        return project + "." + shortNamespace;
    }


    @SneakyThrows
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Field[] fields = bean.getClass().getDeclaredFields();
        if (fields == null || fields.length <= 0) {
            return bean;
        }

        for (Field field : fields) {
            if (field.isAnnotationPresent(Apollo.class)) {
                Apollo anno = field.getAnnotation(Apollo.class);
                String namespace = anno.namespace();
                String key = anno.key();
                ApolloField apolloField = new ApolloField(bean, field, namespace, key, anno.defaultVal());

                /**
                 * 反射赋值
                 */
                logger.info("ApolloPostProcessor setProperty start: apolloFiled={}", apolloField);
                setProperty(apolloField);

                /**
                 * 记录配置项map
                 */
                ApolloConfigItem item = new ApolloConfigItem(namespace, key);
                if (!itemFieldMap.containsKey(item)) {
                    itemFieldMap.put(item, new ArrayList<>());

                    /**
                     * 监听变更
                     */
                    getConfig(namespace).addChangeListener(new ChangeListener(item));
                }
                itemFieldMap.get(item).add(apolloField);

            }
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    private void setProperty(ApolloField apolloField) throws IllegalAccessException {
        String valueStr = getValueStr(apolloField.namespace, apolloField.key, apolloField.defaultVal);
        Object value = getValue(apolloField.field, valueStr);
        apolloField.field.setAccessible(true);
        try {
            apolloField.field.set(apolloField.bean, value);
            logger.info("ApolloPostProcessor setProperty over: apolloField={} value={}", apolloField, value);
        } catch (IllegalAccessException e) {
            logger.error("ApolloPostProcessor error: namespace={} key={}", apolloField.namespace, apolloField.key, e);
            throw e;
        }
    }

    private Object getValue(Field field, String val) {
        /**
         * 1.String 类型
         */
        if (String.class.equals(field.getType())) {
            return val;
        }

        return gson.fromJson(val, field.getGenericType());
    }


    private String getValueStr(String namespace, String key, String defaultVal) {
        return getConfig(namespace).getProperty(key, defaultVal);
    }

    public class ChangeListener implements ConfigChangeListener {
        private ApolloConfigItem item;

        public ChangeListener(ApolloConfigItem item) {
            this.item = item;
        }

        @lombok.SneakyThrows
        @Override
        public void onChange(ConfigChangeEvent changeEvent) {
            if (changeEvent == null) {
                logger.warn("ApolloPostProcessor onChange: changeEvent is null");
                return;
            }
            String namespace = getFullNamespace(item.getNamespace());
            String key = item.getKey();
            if (namespace.equals(changeEvent.getNamespace())) {
                if (changeEvent.changedKeys().contains(key)) {
                    logger.info("ApolloPostProcessor onChange: item={}", gson.toJson(item));
                    for (ApolloField apolloField : itemFieldMap.get(item)) {
                        logger.info("ApolloPostProcessor setProperty start onChange: apolloFiled={}", apolloField);
                        setProperty(apolloField);
                    }
                }
            }
        }
    }

    /**
     * apollo被注解属性
     */
    public static class ApolloField {
        private Object bean;
        private Field field;
        private String namespace;
        private String key;
        private String defaultVal;

        public ApolloField() {
        }

        public ApolloField(Object bean, Field field, String namespace, String key, String defaultVal) {
            this.bean = bean;
            this.field = field;
            this.namespace = namespace;
            this.key = key;
            this.defaultVal = defaultVal;
        }

        public Object getBean() {
            return bean;
        }

        public ApolloField setBean(Object bean) {
            this.bean = bean;
            return this;
        }

        public Field getField() {
            return field;
        }

        public ApolloField setField(Field field) {
            this.field = field;
            return this;
        }

        public String getNamespace() {
            return namespace;
        }

        public ApolloField setNamespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public String getKey() {
            return key;
        }

        public ApolloField setKey(String key) {
            this.key = key;
            return this;
        }

        public String getDefaultVal() {
            return defaultVal;
        }

        public ApolloField setDefaultVal(String defaultVal) {
            this.defaultVal = defaultVal;
            return this;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("[");
            sb.append("bean=").append(bean);
            sb.append(", field=").append(field);
            sb.append(", namespace=").append(namespace);
            sb.append(", key=").append(key);
            sb.append(", defaultVal=").append(defaultVal);
            sb.append("]");
            return sb.toString();
        }
    }

    /**
     * apollo配置项
     */
    public static class ApolloConfigItem {
        private String namespace;
        private String key;

        public ApolloConfigItem() {
        }

        public ApolloConfigItem(String namespace, String key) {
            this.namespace = namespace;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ApolloConfigItem that = (ApolloConfigItem) o;
            return Objects.equals(namespace, that.namespace) &&
                    Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(namespace, key);
        }

        public String getNamespace() {
            return namespace;
        }

        public ApolloConfigItem setNamespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public String getKey() {
            return key;
        }

        public ApolloConfigItem setKey(String key) {
            this.key = key;
            return this;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("[");
            sb.append("namespace=").append(namespace);
            sb.append(", key=").append(key);
            sb.append("]");
            return sb.toString();
        }
    }
}
