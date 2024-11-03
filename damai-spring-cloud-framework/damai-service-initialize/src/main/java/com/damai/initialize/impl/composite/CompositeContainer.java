package com.damai.initialize.impl.composite;

import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 设计模式之组合模式( Composite Pattern )
 * CompositeContainer类是一个泛型类，用于管理和执行一组抽象组件（AbstractComposite）。
 * 它通过Spring的 ConfigurableApplicationContext 初始化组件，并根据组件的类型将它们分组。
 * 组件按照它们的执行顺序和层级关系被组织成树形结构，以便在执行时按照特定的顺序调用它们。
 *
 * @param <T> 参数类型，表示传递给组件执行方法的参数类型。
 */
public class CompositeContainer<T> {

    // 存储所有组件接口的映射，键是组件类型，值是该类型的组件树的根节点。
    private final Map<String, AbstractComposite> allCompositeInterfaceMap = new HashMap<>();

    /**
     * 初始化方法，从Spring应用上下文中获取所有AbstractComposite类型的bean，
     * 并根据它们的类型分组，构建组件树，并存储在allCompositeInterfaceMap中。
     *
     * @param applicationEvent Spring的ConfigurableApplicationContext，用于获取bean。
     */
    public void init(ConfigurableApplicationContext applicationEvent) {
        // 获取所有AbstractComposite类型的bean。
        Map<String, AbstractComposite> compositeInterfaceMap = applicationEvent.getBeansOfType(AbstractComposite.class);

        // 根据组件的类型对组件进行分组。
        Map<String, List<AbstractComposite>> collect = compositeInterfaceMap.values().stream().collect(Collectors.groupingBy(AbstractComposite::type));
        // 遍历分组后的组件，构建组件树，并存储在allCompositeInterfaceMap中。
        collect.forEach((k, v) -> {
            AbstractComposite root = build(v);
            if (Objects.nonNull(root)) {
                allCompositeInterfaceMap.put(k, root);
            }
        });
    }

    /**
     * 根据类型执行对应的组件树。
     * 如果指定类型的组件树不存在，则抛出异常。
     *
     * @param type  组件树的类型。
     * @param param 传递给组件执行方法的参数。
     * @throws DaMaiFrameException 如果指定类型的组件树不存在。
     */
    public void execute(String type, T param) {
        // 获取指定类型的组件树的根节点。
        AbstractComposite compositeInterface = Optional.ofNullable(allCompositeInterfaceMap.get(type))
                .orElseThrow(() -> new DaMaiFrameException(BaseCode.COMPOSITE_NOT_EXIST));
        // 执行组件树。
        compositeInterface.allExecute(param);
    }

    /**
     * 构建组件树的辅助方法。
     *
     * @param groupedByTier 按层级组织的组件映射，键为层级编号，值为该层级的组件映射。
     * @param currentTier   当前处理的层级编号。
     */
    private static void buildTree(Map<Integer, Map<Integer, AbstractComposite>> groupedByTier, int currentTier) {
        // 获取当前层级的所有组件
        Map<Integer, AbstractComposite> currentLevelComponents = groupedByTier.get(currentTier);

        // 获取下一层级的所有组件
        Map<Integer, AbstractComposite> nextLevelComponents = groupedByTier.get(currentTier + 1);

        // 如果当前层级没有组件，直接返回
        if (currentLevelComponents == null) {
            return;
        }

        // 如果下一层级有组件，开始构建层级关系
        if (nextLevelComponents != null) {
            // 遍历下一层级的所有组件
            for (AbstractComposite child : nextLevelComponents.values()) {
                // 获取子组件的父组件顺序号
                Integer parentOrder = child.executeParentOrder();

                // 如果父组件顺序号为空或为0，跳过该子组件
                if (parentOrder == null || parentOrder == 0) {
                    continue;
                }

                // 获取父组件
                AbstractComposite parent = currentLevelComponents.get(parentOrder);

                // 如果父组件存在，将子组件添加到父组件中
                if (parent != null) {
                    parent.add(child);
                }
            }
        }

        // 递归调用，处理下一层级
        buildTree(groupedByTier, currentTier + 1);
    }

    /**
     * 根据提供的组件集合构建组件树，并返回根节点。
     *
     * @param components 组件集合。
     * @return 根节点。
     */
    private static AbstractComposite build(Collection<AbstractComposite> components) {
        // 创建一个按层级组织的组件映射
        Map<Integer, Map<Integer, AbstractComposite>> groupedByTier = new TreeMap<>();

        // 遍历所有组件，按层级和顺序号进行分组
        for (AbstractComposite component : components) {
            groupedByTier.computeIfAbsent(component.executeTier(), k -> new HashMap<>(16))
                    .put(component.executeOrder(), component);
        }

        // 获取最小的层级编号
        Integer minTier = groupedByTier.keySet().stream().min(Integer::compare).orElse(null);

        // 如果没有组件，返回null
        if (minTier == null) {
            return null;
        }

        // 从最小的层级开始构建组件树
        buildTree(groupedByTier, minTier);

        // 返回根节点，根节点的父组件顺序号为null或0
        return groupedByTier.get(minTier).values().stream()
                .filter(c -> c.executeParentOrder() == null || c.executeParentOrder() == 0)
                .findFirst()
                .orElse(null);
    }

}
