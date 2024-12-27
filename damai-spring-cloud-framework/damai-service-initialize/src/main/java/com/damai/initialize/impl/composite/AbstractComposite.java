package com.damai.initialize.impl.composite;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * 组合模式基类，用于构建树形结构的组件。
 * 该类提供了添加子组件、执行自身及子组件操作的方法。
 * 子类需要实现具体的 `execute` 方法以定义具体的业务逻辑。
 *
 * @param <T> 泛型参数，表示传递给业务操作的参数类型
 */
public abstract class AbstractComposite<T> {

	/**
	 * 存储子节点的列表
	 */
	protected List<AbstractComposite<T>> list = new ArrayList<>();

	/**
	 * 执行具体业务的抽象方法，由子类具体实现。
	 *
	 * @param param 泛型参数，用于业务执行。
	 */
	protected abstract void execute(T param);

	/**
	 * 获取返回组件的类型
	 *
	 * @return 返回组件的类型。
	 */
	public abstract String type();

	/**
	 * 返回父级执行顺序，用于建立层级关系.(根节点的话返回值为0)
	 *
	 * @return 返回父级执行顺序，用于建立层级关系.(根节点的话返回值为0)
	 */
	public abstract Integer executeParentOrder();

	/**
	 * 返回组件的执行层级
	 *
	 * @return 返回组件的执行层级
	 */
	public abstract Integer executeTier();

	/**
	 * 返回组件在同一层级中的执行顺序
	 *
	 * @return 返回组件在同一层级中的执行顺序
	 */
	public abstract Integer executeOrder();

	/**
	 * 将子组件添加到当前组件的子列表中
	 *
	 * @param abstractComposite 子组件实例
	 */
	public void add(AbstractComposite<T> abstractComposite) {
		list.add(abstractComposite);
	}

	/**
	 * 按层次结构执行每个组件的业务逻辑。
	 * 此方法使用广度优先搜索算法来遍历树结构，并执行每个节点的操作
	 *
	 * @param param 传递给每个节点操作的参数
	 */
	public void allExecute(T param) {
		// 创建一个队列来存储待处理的复合对象
		Queue<AbstractComposite<T>> queue = new LinkedList<>();
		// 将当前复合对象添加到队列中作为遍历的起点
		queue.add(this);
		// 当队列不为空时，继续处理
		while (!queue.isEmpty()) {
			// 获取当前层级的复合对象数量
			int levelSize = queue.size();
			// 遍历当前层级的所有复合对象
			for (int i = 0; i < levelSize; i++) {
				// 从队列中取出下一个复合对象
				AbstractComposite<T> current = queue.poll();
				// 确保取出的复合对象不为空
				assert current != null;
				// 执行当前复合对象的操作
				current.execute(param);
				// 将当前复合对象的所有子对象添加到队列中
				queue.addAll(current.list);
			}
		}
	}
}
