package com.damai.service.composite;

import com.damai.dto.ProgramGetDto;
import com.damai.enums.BaseCode;
import com.damai.enums.CompositeCheckType;
import com.damai.exception.DaMaiFrameException;
import com.damai.handler.BloomFilterHandler;
import com.damai.initialize.impl.composite.AbstractComposite;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 节目布隆过滤器检查处理器
 * 继承自AbstractComposite，用于在复合检查流程中执行布隆过滤器检查
 * 主要作用是检查节目ID是否存在于布隆过滤器中，以快速判断节目是否存在
 */
@Component
public class ProgramBloomFilterCheckHandler extends AbstractComposite<ProgramGetDto> {

	@Autowired
	private BloomFilterHandler bloomFilterHandler;

	/**
	 * 执行布隆过滤器检查
	 *
	 * @param param 节目获取DTO，包含需要检查的节目ID
	 * @throws DaMaiFrameException 如果节目ID不在布隆过滤器中，则抛出节目不存在异常
	 */
	@Override
	protected void execute(final ProgramGetDto param) {
		boolean contains = bloomFilterHandler.contains(String.valueOf(param.getId()));
		if (!contains) {
			throw new DaMaiFrameException(BaseCode.PROGRAM_NOT_EXIST);
		}
	}

	/**
	 * 返回检查类型
	 *
	 * @return 检查类型的值
	 */
	@Override
	public String type() {
		return CompositeCheckType.PROGRAM_DETAIL_CHECK.getValue();
	}

	/**
	 * 返回执行父级顺序
	 *
	 * @return 执行父级顺序的值，此处返回0表示无父级检查
	 */
	@Override
	public Integer executeParentOrder() {
		return 0;
	}

	/**
	 * 返回执行层级
	 *
	 * @return 执行层级的值，此处返回1表示第一层级
	 */
	@Override
	public Integer executeTier() {
		return 1;
	}

	/**
	 * 返回执行顺序
	 *
	 * @return 执行顺序的值，此处返回1表示第一个执行
	 */
	@Override
	public Integer executeOrder() {
		return 1;
	}
}
