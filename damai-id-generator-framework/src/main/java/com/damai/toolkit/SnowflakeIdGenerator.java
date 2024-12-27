package com.damai.toolkit;

import cn.hutool.core.date.SystemClock;
import cn.hutool.core.lang.Assert;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;


/**
 * SnowflakeIdGenerator 是一个分布式唯一ID生成器。
 * 它使用 Twitter Snowflake 算法生成唯一ID。
 * ID 结构如下：1位符号位（始终为0），41位时间戳，5位数据中心ID，5位工作节点ID，12位序列号。
 */
@Slf4j
public class SnowflakeIdGenerator {

	/**
	 * Snowflake算法的起始时间戳，用于计算相对时间戳
	 */
	private static final long BASIS_TIME = 1288834974657L;

	/**
	 * 工作机器ID所占的位数
	 */
	private final long workerIdBits = 5L;

	/**
	 * 数据中心ID所占的位数
	 */
	private final long datacenterIdBits = 5L;

	/**
	 * 允许的最大工作机器ID，根据工作机器ID的位数计算得出
	 */
	private final long maxWorkerId = -1L ^ (-1L << workerIdBits);

	/**
	 * 允许的最大数据中心ID，根据数据中心ID的位数计算得出
	 */
	private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);

	/**
	 * 序列号所占的位数
	 */
	private final long sequenceBits = 12L;

	/**
	 * 工作机器ID左移的位数，等于序列号所占的位数
	 */
	private final long workerIdShift = sequenceBits;

	/**
	 * 数据中心ID左移的位数，等于序列号和工作机器ID所占的位数之和
	 */
	private final long datacenterIdShift = sequenceBits + workerIdBits;

	/**
	 * 时间戳左移的位数，等于序列号、工作机器ID和数据中心ID所占的位数之和
	 */
	private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;

	/**
	 * 序列号的掩码，用于获取序列号部分
	 */
	private final long sequenceMask = -1L ^ (-1L << sequenceBits);

	/**
	 * 工作机器ID
	 */
	private final long workerId;

	/**
	 * 数据中心ID
	 */
	private final long datacenterId;

	/**
	 * 序列号，用于同一毫秒内产生的不同ID
	 */
	private long sequence = 0L;

	/**
	 * 上一个时间戳，用于判断是否需要更新时间戳
	 */
	private long lastTimestamp = -1L;

	/**
	 * 当前机器的IP地址，可能用于分布式环境下的机器识别
	 */
	private InetAddress inetAddress;

	/**
	 * 构造函数，使用 WorkDataCenterId 对象初始化 SnowflakeIdGenerator。
	 * 如果数据中⼼ID不为空，则使用提供的工作节点ID和数据中⼼ID。
	 * 否则，自动⽣成数据中⼼ID和工作节点ID。
	 *
	 * @param workDataCenterId 包含工作节点ID和数据中⼼ID
	 */
	public SnowflakeIdGenerator(WorkDataCenterId workDataCenterId) {
		if (Objects.nonNull(workDataCenterId.getDataCenterId())) {
			this.workerId = workDataCenterId.getWorkId();
			this.datacenterId = workDataCenterId.getDataCenterId();
		}
		else {
			this.datacenterId = getDatacenterId(maxDatacenterId);
			workerId = getMaxWorkerId(datacenterId, maxWorkerId);
		}
	}

	/**
	 * 构造函数，使用 InetAddress 对象初始化 SnowflakeIdGenerator。
	 * 自动生成数据中⼼ID和工作节点ID，并初始化日志。
	 *
	 * @param inetAddress 网络地址信息
	 */
	public SnowflakeIdGenerator(InetAddress inetAddress) {
		this.inetAddress = inetAddress;
		this.datacenterId = getDatacenterId(maxDatacenterId);
		this.workerId = getMaxWorkerId(datacenterId, maxWorkerId);
		initLog();
	}

	/**
	 * 初始化日志，记录数据中⼼ID和工作节点ID。
	 */
	private void initLog() {
		if (log.isDebugEnabled()) {
			log.debug("初始化 SnowflakeIdGenerator 数据中⼼ID:" + this.datacenterId + " 工作节点ID:" + this.workerId);
		}
	}

	/**
	 * 构造函数，使用指定的工作节点ID和数据中⼼ID初始化 SnowflakeIdGenerator。
	 * 检查工作节点ID和数据中⼼ID是否在有效范围内。
	 *
	 * @param workerId     工作节点ID
	 * @param datacenterId 数据中⼼ID
	 */
	public SnowflakeIdGenerator(long workerId, long datacenterId) {
		Assert.isFalse(workerId > maxWorkerId || workerId < 0,
				String.format("工作节点ID不能大于 %d 或小于 0", maxWorkerId));
		Assert.isFalse(datacenterId > maxDatacenterId || datacenterId < 0,
				String.format("数据中⼼ID不能大于 %d 或小于 0", maxDatacenterId));
		this.workerId = workerId;
		this.datacenterId = datacenterId;
		initLog();
	}

	/**
	 * 获取最大工作节点ID。
	 * 根据数据中⼼ID和最大工作节点ID生成一个唯一的工作节点ID。
	 *
	 * @param datacenterId 数据中⼼ID
	 * @param maxWorkerId  最大工作节点ID
	 * @return 生成的工作节点ID
	 */
	protected long getMaxWorkerId(long datacenterId, long maxWorkerId) {
		StringBuilder mpid = new StringBuilder();
		mpid.append(datacenterId);
		String name = ManagementFactory.getRuntimeMXBean().getName();
		if (StringUtils.isNotBlank(name)) {
			mpid.append(name.split("@")[0]);
		}
		return (mpid.toString().hashCode() & 0xffff) % (maxWorkerId + 1);
	}

	/**
	 * 获取数据中心ID
	 * 通过网络接口的MAC地址生成一个唯一的数据中心ID
	 *
	 * @param maxDatacenterId 最大的数据中心ID，用于限定生成ID的范围
	 * @return 生成的数据中心ID
	 */
	protected long getDatacenterId(long maxDatacenterId) {
		long id = 0L;
		try {
			// 检查是否已经获取了本地主机地址，若未获取则进行初始化
			if (null == this.inetAddress) {
				this.inetAddress = InetAddress.getLocalHost();
			}
			// 根据本地主机地址获取网络接口
			NetworkInterface network = NetworkInterface.getByInetAddress(this.inetAddress);
			// 如果网络接口为空，则设置数据中心ID为1
			if (null == network) {
				id = 1L;
			}
			else {
				// 获取网络接口的MAC地址
				byte[] mac = network.getHardwareAddress();
				// 如果MAC地址不为空，则通过MAC地址的最后两个字节计算数据中心ID
				if (null != mac) {
					id = ((0x000000FF & (long) mac[mac.length - 2]) | (0x0000FF00 & (((long) mac[mac.length - 1]) << 8))) >> 6;
					// 确保生成的数据中心ID在允许的范围内
					id = id % (maxDatacenterId + 1);
				}
			}
		}
		catch (Exception e) {
			// 异常处理：记录获取数据中心ID过程中的异常信息
			log.warn("获取数据中⼼ID: " + e.getMessage());
		}
		// 返回计算得到的数据中心ID
		return id;
	}


	/**
	 * 获取基础时间戳。
	 * 处理时间回拨问题，确保生成的时间戳始终递增。
	 * Mermaid Flowchart:
	 * flowchart TD
	 * A[开始] --> B{是否在同一毫秒内}
	 * B -->|是| C[序列号自增并取掩码]
	 * C --> D{序列号是否达到最大值}
	 * D -->|是| E[等待下一毫秒]
	 * D -->|否| F[更新时间戳]
	 * B -->|否| G[生成随机序列号]
	 * G --> F
	 * E --> F
	 * F --> H[结束]
	 * <p>
	 * 开始：进入 getBase 方法。
	 * 是否在同一毫秒内：检查当前时间戳是否与上次时间戳相同。
	 * 序列号自增并取掩码：如果在同一毫秒内，序列号自增并取掩码。
	 * 序列号是否达到最大值：检查序列号是否达到最大值。
	 * 等待下一毫秒：如果序列号达到最大值，等待下一毫秒。
	 * 生成随机序列号：如果不在同一毫秒内，生成随机序列号。
	 * 更新时间戳：更新 lastTimestamp。
	 * 结束：返回当前时间戳。
	 *
	 * @return 当前时间戳
	 */
	public long getBaseTimestamp() {
		int five = 5;
		long timestamp = timeGen();
		// 处理时间回拨
		if (timestamp < lastTimestamp) {
			long offset = lastTimestamp - timestamp;
			if (offset <= five) {
				try {
					wait(offset << 1);
					timestamp = timeGen();
					if (timestamp < lastTimestamp) {
						throw new RuntimeException(String.format("时间回拨. 拒绝生成ID %d 毫秒", offset));
					}
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			else {
				throw new RuntimeException(String.format("时间回拨. 拒绝生成ID %d 毫秒", offset));
			}
		}

		if (lastTimestamp == timestamp) {
			// 相同毫秒内，序列号自增
			sequence = (sequence + 1) & sequenceMask;
			if (sequence == 0) {
				// 同一毫秒的序列数已经达到最大
				timestamp = tilNextMillis(lastTimestamp);
			}
		}
		else {
			// 不同毫秒内，序列号置为 1 - 2 随机数
			sequence = ThreadLocalRandom.current().nextLong(1, 3);
		}

		lastTimestamp = timestamp;

		return timestamp;
	}

	/**
	 * 生成下一个唯一ID。
	 * 使用时间戳、数据中⼼ID、工作节点ID和序列号组合生成唯一ID。
	 *
	 * @return 唯一ID
	 */
	public synchronized long nextId() {
		long timestamp = getBaseTimestamp();
		// 时间戳部分 | 数据中心部分 | 机器标识部分 | 序列号部分
		return ((timestamp - BASIS_TIME) << timestampLeftShift)
				| (datacenterId << datacenterIdShift)
				| (workerId << workerIdShift)
				| sequence;
	}

	/**
	 * 基因法改造雪花算法生成带有用户ID和表计数的唯一订单号。
	 * 使用时间戳、数据中⼼ID、工作节点ID、序列号和用户ID组合生成唯一订单号。
	 *
	 * @param userId     用户ID
	 * @param tableCount 表计数
	 * @return 唯一订单号
	 */
	public synchronized long getOrderNumber(long userId, long tableCount) {
		long timestamp = getBaseTimestamp();
		long sequenceShift = log2N(tableCount);
		// 时间戳部分 | 数据中心部分 | 机器标识部分 | 序列号部分 | 用户id基因
		return ((timestamp - BASIS_TIME) << timestampLeftShift)
				| (datacenterId << datacenterIdShift)
				| (workerId << workerIdShift)
				| (sequence << sequenceShift)
				| (userId % tableCount);
	}

	/**
	 * 获取下一个毫秒的时间戳。
	 * 确保时间戳始终递增。
	 * "til"，表示“直到”
	 * flowchart TD
	 * A[开始] --> B{当前时间戳是否大于上次时间戳}
	 * B -->|否| C[获取当前时间戳]
	 * C --> B
	 * B -->|是| D[返回当前时间戳]
	 * D --> E[结束]
	 * <p>
	 * 开始：进入 tilNextMillis 方法。
	 * 当前时间戳是否大于上次时间戳：检查当前时间戳是否大于上次时间戳。
	 * 获取当前时间戳：如果当前时间戳不大于上次时间戳，继续获取当前时间戳。
	 * 返回当前时间戳：如果当前时间戳大于上次时间戳，返回当前时间戳。
	 * 结束：方法结束。
	 *
	 * @param lastTimestamp 上一次的时间戳
	 * @return 下一个毫秒的时间戳
	 */
	protected long tilNextMillis(long lastTimestamp) {
		long timestamp = timeGen();
		while (timestamp <= lastTimestamp) {
			timestamp = timeGen();
		}
		return timestamp;
	}

	/**
	 * 获取当前时间戳。
	 *
	 * @return 当前时间戳
	 */
	protected long timeGen() {
		return SystemClock.now();
	}

	/**
	 * 解析ID中的时间戳。
	 *
	 * @param id 唯一ID
	 * @return 解析出的时间戳
	 */
	public static long parseIdTimestamp(long id) {
		return (id >> 22) + BASIS_TIME;
	}

	/**
	 * 计算以2为底的对数。
	 *
	 * @param count 数值
	 * @return 以2为底的对数
	 */
	public long log2N(long count) {
		// 运用了对数的一个运算性质
		// log_a(b) = log_c(b) / log_c(a)
		// 此处为 log_a(b) = ln(b) / ln(a)
		return (long) (Math.log(count) / Math.log(2));
	}

	/**
	 * 获取最大工作节点ID。
	 *
	 * @return 最大工作节点ID
	 */
	public long getMaxWorkerId() {
		return maxWorkerId;
	}

	/**
	 * 获取最大数据中⼼ID。
	 *
	 * @return 最大数据中⼼ID
	 */
	public long getMaxDatacenterId() {
		return maxDatacenterId;
	}
}
