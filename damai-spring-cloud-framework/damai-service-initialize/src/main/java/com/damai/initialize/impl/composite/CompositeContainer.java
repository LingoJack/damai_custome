package com.damai.initialize.impl.composite;

import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.*;
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

	// 存储所有组件接口的映射，键是组件类型(type)，值是该类型的组件树的根节点(root)。
	private final Map<String, AbstractComposite> allCompositeInterfaceMap = new HashMap<>();

	/**
	 * 构建组件树的辅助方法。
	 *
	 * @param mapGroupedByTier 按层级组织的组件映射。Map<Tier, Map<Order, Component>>
	 * @param currentTier      当前处理的层级。
	 */
	private static void buildTree(Map<Integer, Map<Integer, AbstractComposite>> mapGroupedByTier, int currentTier) {
		Map<Integer, AbstractComposite> currentLevelComponents = mapGroupedByTier.get(currentTier);
		Map<Integer, AbstractComposite> nextLevelComponents = mapGroupedByTier.get(currentTier + 1);

		if (currentLevelComponents == null) {
			// 当前层级没有组件时，直接返回
			return;
		}

		if (nextLevelComponents != null) {
			for (AbstractComposite child : nextLevelComponents.values()) {
				Integer parentOrder = child.executeParentOrder();
				if (parentOrder == null || parentOrder == 0) {
					// 跳过根节点
					continue;
				}
				AbstractComposite parent = currentLevelComponents.get(parentOrder);
				if (parent != null) {
					// 将子节点添加到父节点的子列表中
					parent.add(child);
				}
			}
		}
		// 递归构建下一层级的树结构
		buildTree(mapGroupedByTier, currentTier + 1);
	}

	/**
	 * 根据提供的组件集合构建组件树，并返回根节点。
	 *
	 * @param components 组件集合。
	 * @return 根节点。
	 */
	private static AbstractComposite build(Collection<AbstractComposite> components) {
		// 创建一个按层级组织的组件映射，按层级和执行顺序组织组件
		// key 为 层级，value 为该层级的组件映射 Map，该 Map 的 key 为执行顺序号，value 为组件对象
		// Map<Tier, Map<Order, Component>>
		Map<Integer, Map<Integer, AbstractComposite>> mapGroupedByTier = new TreeMap<>();

		// 遍历所有组件，按层级和顺序号进行分组
		for (AbstractComposite component : components) {
			mapGroupedByTier
					.computeIfAbsent(component.executeTier(), k -> new HashMap<>(16))
					.put(component.executeOrder(), component); // 使用 executeOrder 作为键
		}

		// 获取最小的层级编号
		Integer minTier = mapGroupedByTier.keySet().stream().min(Integer::compare).orElse(null);

		// 如果没有组件，即最小层级不存在，返回null
		if (minTier == null) {
			return null;
		}

		// 从最小的层级开始构建组件树
		buildTree(mapGroupedByTier, minTier);

		// 返回根节点，根节点的父组件顺序号为null或0
		return mapGroupedByTier
				.get(minTier)
				.values()
				.stream()
				.filter(c -> c.executeParentOrder() == null || c.executeParentOrder() == 0)
				.findFirst()
				.orElse(null);
	}

	/**
	 * 初始化方法，从Spring应用上下文中获取所有{@link AbstractComposite}类型的bean，
	 * 并根据它们的类型分组，构建组件树，并存储在{@link CompositeContainer#allCompositeInterfaceMap}中。
	 *
	 * @param applicationEvent Spring的ConfigurableApplicationContext，用于获取bean。
	 */
	public void init(ConfigurableApplicationContext applicationEvent) {
		// 获取所有 AbstractComposite 类型的 Bean
		Map<String, AbstractComposite> compositeInterfaceMap = applicationEvent.getBeansOfType(AbstractComposite.class);
		// 查找出AbstractComposite类型，然后根据type进行分组
		Map<String, List<AbstractComposite>> map = compositeInterfaceMap.values()
				.stream()
				.collect(Collectors.groupingBy(AbstractComposite::type));

		// 遍历每个类型的组件列表
		map.forEach((type, list) -> {
			// 构建组件树结构
			AbstractComposite root = build(list);
			// 如果根节点存在，则执行业务逻辑
			if (Objects.nonNull(root)) {
				allCompositeInterfaceMap.put(type, root);
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

}
