package com.damai.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baidu.fsg.uid.UidGenerator;
import com.damai.client.OrderClient;
import com.damai.common.ApiResponse;
import com.damai.core.RedisKeyManage;
import com.damai.dto.*;
import com.damai.entity.ProgramShowTime;
import com.damai.enums.BaseCode;
import com.damai.enums.OrderStatus;
import com.damai.enums.SellStatus;
import com.damai.exception.DaMaiFrameException;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.delaysend.DelayOrderCancelSend;
import com.damai.service.kafka.CreateOrderMqDomain;
import com.damai.service.kafka.CreateOrderSend;
import com.damai.service.lua.ProgramCacheCreateOrderData;
import com.damai.service.lua.ProgramCacheCreateOrderResolutionOperate;
import com.damai.service.lua.ProgramCacheResolutionOperate;
import com.damai.service.tool.SeatMatch;
import com.damai.util.DateUtils;
import com.damai.vo.ProgramVo;
import com.damai.vo.SeatVo;
import com.damai.vo.TicketCategoryVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.damai.service.constant.ProgramOrderConstant.ORDER_TABLE_COUNT;

/**
 * 节目订单 service
 **/
@Slf4j
@Service
public class ProgramOrderService {

	@Autowired
	ProgramCacheCreateOrderResolutionOperate programCacheCreateOrderResolutionOperate;

	@Autowired
	private OrderClient orderClient;

	@Autowired
	private UidGenerator uidGenerator;

	@Autowired
	private ProgramCacheResolutionOperate programCacheResolutionOperate;

	@Autowired
	private DelayOrderCancelSend delayOrderCancelSend;

	@Autowired
	private CreateOrderSend createOrderSend;

	@Autowired
	private ProgramService programService;

	@Autowired
	private ProgramShowTimeService programShowTimeService;

	@Autowired
	private TicketCategoryService ticketCategoryService;

	@Autowired
	private SeatService seatService;

	public List<TicketCategoryVo> getTicketCategoryList(ProgramOrderCreateDto programOrderCreateDto, Date showTime) {
		List<TicketCategoryVo> getTicketCategoryVoList = new ArrayList<>();
		List<TicketCategoryVo> ticketCategoryVoList =
				ticketCategoryService.selectTicketCategoryListByProgramIdMultipleCache(programOrderCreateDto.getProgramId(),
						showTime);
		Map<Long, TicketCategoryVo> ticketCategoryVoMap =
				ticketCategoryVoList.stream()
						.collect(Collectors.toMap(TicketCategoryVo::getId, ticketCategoryVo -> ticketCategoryVo));
		List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
		if (CollectionUtil.isNotEmpty(seatDtoList)) {
			for (SeatDto seatDto : seatDtoList) {
				TicketCategoryVo ticketCategoryVo = ticketCategoryVoMap.get(seatDto.getTicketCategoryId());
				if (Objects.nonNull(ticketCategoryVo)) {
					getTicketCategoryVoList.add(ticketCategoryVo);
				}
				else {
					throw new DaMaiFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2);
				}
			}
		}
		else {
			TicketCategoryVo ticketCategoryVo = ticketCategoryVoMap.get(programOrderCreateDto.getTicketCategoryId());
			if (Objects.nonNull(ticketCategoryVo)) {
				getTicketCategoryVoList.add(ticketCategoryVo);
			}
			else {
				throw new DaMaiFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2);
			}
		}
		return getTicketCategoryVoList;
	}

	public String create(ProgramOrderCreateDto programOrderCreateDto) {
		ProgramShowTime programShowTime =
				programShowTimeService.selectProgramShowTimeByProgramIdMultipleCache(programOrderCreateDto.getProgramId());
		List<TicketCategoryVo> getTicketCategoryList =
				getTicketCategoryList(programOrderCreateDto, programShowTime.getShowTime());
		BigDecimal parameterOrderPrice = new BigDecimal("0");
		BigDecimal databaseOrderPrice = new BigDecimal("0");
		List<SeatVo> purchaseSeatList = new ArrayList<>();
		List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
		List<SeatVo> seatVoList = new ArrayList<>();
		Map<String, Long> ticketCategoryRemainNumber = new HashMap<>(16);
		for (TicketCategoryVo ticketCategory : getTicketCategoryList) {
			List<SeatVo> allSeatVoList =
					seatService.selectSeatResolution(programOrderCreateDto.getProgramId(), ticketCategory.getId(),
							DateUtils.countBetweenSecond(DateUtils.now(), programShowTime.getShowTime()), TimeUnit.SECONDS);
			seatVoList.addAll(allSeatVoList.stream().
					filter(seatVo -> seatVo.getSellStatus().equals(SellStatus.NO_SOLD.getCode())).toList());
			ticketCategoryRemainNumber.putAll(ticketCategoryService.getRedisRemainNumberResolution(
					programOrderCreateDto.getProgramId(), ticketCategory.getId()));
		}
		if (CollectionUtil.isNotEmpty(seatDtoList)) {
			Map<Long, Long> seatTicketCategoryDtoCount = seatDtoList.stream()
					.collect(Collectors.groupingBy(SeatDto::getTicketCategoryId, Collectors.counting()));
			for (Entry<Long, Long> entry : seatTicketCategoryDtoCount.entrySet()) {
				Long ticketCategoryId = entry.getKey();
				Long purchaseCount = entry.getValue();
				Long remainNumber = Optional.ofNullable(ticketCategoryRemainNumber.get(String.valueOf(ticketCategoryId)))
						.orElseThrow(() -> new DaMaiFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2));
				if (purchaseCount > remainNumber) {
					throw new DaMaiFrameException(BaseCode.TICKET_REMAIN_NUMBER_NOT_SUFFICIENT);
				}
			}
			for (SeatDto seatDto : seatDtoList) {
				Map<String, SeatVo> seatVoMap = seatVoList.stream().collect(Collectors
						.toMap(seat -> seat.getRowCode() + "-" + seat.getColCode(), seat -> seat, (v1, v2) -> v2));
				SeatVo seatVo = seatVoMap.get(seatDto.getRowCode() + "-" + seatDto.getColCode());
				if (Objects.isNull(seatVo)) {
					throw new DaMaiFrameException(BaseCode.SEAT_IS_NOT_NOT_SOLD);
				}
				purchaseSeatList.add(seatVo);
				parameterOrderPrice = parameterOrderPrice.add(seatDto.getPrice());
				databaseOrderPrice = databaseOrderPrice.add(seatVo.getPrice());
			}
			if (parameterOrderPrice.compareTo(databaseOrderPrice) > 0) {
				throw new DaMaiFrameException(BaseCode.PRICE_ERROR);
			}
		}
		else {
			Long ticketCategoryId = programOrderCreateDto.getTicketCategoryId();
			Integer ticketCount = programOrderCreateDto.getTicketCount();
			Long remainNumber = Optional.ofNullable(ticketCategoryRemainNumber.get(String.valueOf(ticketCategoryId)))
					.orElseThrow(() -> new DaMaiFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2));
			if (ticketCount > remainNumber) {
				throw new DaMaiFrameException(BaseCode.TICKET_REMAIN_NUMBER_NOT_SUFFICIENT);
			}
			purchaseSeatList = SeatMatch.findAdjacentSeatVos(seatVoList.stream().filter(seatVo ->
					Objects.equals(seatVo.getTicketCategoryId(), ticketCategoryId)).collect(Collectors.toList()), ticketCount);
			if (purchaseSeatList.size() < ticketCount) {
				throw new DaMaiFrameException(BaseCode.SEAT_OCCUPY);
			}
		}
		updateProgramCacheDataResolution(programOrderCreateDto.getProgramId(), purchaseSeatList, OrderStatus.NO_PAY);
		return doCreate(programOrderCreateDto, purchaseSeatList);
	}


	public String createNew(ProgramOrderCreateDto programOrderCreateDto) {
		List<SeatVo> purchaseSeatList = createOrderOperateProgramCacheResolution(programOrderCreateDto);
		return doCreate(programOrderCreateDto, purchaseSeatList);
	}

	public String createNewAsync(ProgramOrderCreateDto programOrderCreateDto) {
		List<SeatVo> purchaseSeatList = createOrderOperateProgramCacheResolution(programOrderCreateDto);
		return doCreateV2(programOrderCreateDto, purchaseSeatList);
	}

	public List<SeatVo> createOrderOperateProgramCacheResolution(ProgramOrderCreateDto programOrderCreateDto) {
		ProgramShowTime programShowTime =
				programShowTimeService.selectProgramShowTimeByProgramIdMultipleCache(programOrderCreateDto.getProgramId());
		List<TicketCategoryVo> getTicketCategoryList =
				getTicketCategoryList(programOrderCreateDto, programShowTime.getShowTime());
		for (TicketCategoryVo ticketCategory : getTicketCategoryList) {
			seatService.selectSeatResolution(programOrderCreateDto.getProgramId(), ticketCategory.getId(),
					DateUtils.countBetweenSecond(DateUtils.now(), programShowTime.getShowTime()), TimeUnit.SECONDS);
			ticketCategoryService.getRedisRemainNumberResolution(
					programOrderCreateDto.getProgramId(), ticketCategory.getId());
		}
		Long programId = programOrderCreateDto.getProgramId();
		List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
		List<String> keys = new ArrayList<>();
		String[] data = new String[2];
		JSONArray jsonArray = new JSONArray();
		JSONArray addSeatDatajsonArray = new JSONArray();
		if (CollectionUtil.isNotEmpty(seatDtoList)) {
			keys.add("1");
			Map<Long, List<SeatDto>> seatTicketCategoryDtoCount = seatDtoList.stream()
					.collect(Collectors.groupingBy(SeatDto::getTicketCategoryId));
			for (Entry<Long, List<SeatDto>> entry : seatTicketCategoryDtoCount.entrySet()) {
				Long ticketCategoryId = entry.getKey();
				int ticketCount = entry.getValue().size();
				JSONObject jsonObject = new JSONObject();
				jsonObject.put("programTicketRemainNumberHashKey", RedisKeyBuild.createRedisKey(
						RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, ticketCategoryId).getRelKey());
				jsonObject.put("ticketCategoryId", ticketCategoryId);
				jsonObject.put("ticketCount", ticketCount);
				jsonArray.add(jsonObject);

				JSONObject seatDatajsonObject = new JSONObject();
				seatDatajsonObject.put("seatNoSoldHashKey", RedisKeyBuild.createRedisKey(
						RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, ticketCategoryId).getRelKey());
				seatDatajsonObject.put("seatDataList", JSON.toJSONString(seatDtoList));
				addSeatDatajsonArray.add(seatDatajsonObject);
			}
		}
		else {
			keys.add("2");
			Long ticketCategoryId = programOrderCreateDto.getTicketCategoryId();
			Integer ticketCount = programOrderCreateDto.getTicketCount();
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("programTicketRemainNumberHashKey", RedisKeyBuild.createRedisKey(
					RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, ticketCategoryId).getRelKey());
			jsonObject.put("ticketCategoryId", ticketCategoryId);
			jsonObject.put("ticketCount", ticketCount);
			jsonObject.put("seatNoSoldHashKey", RedisKeyBuild.createRedisKey(
					RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, ticketCategoryId).getRelKey());
			jsonArray.add(jsonObject);
		}
		keys.add(RedisKeyBuild.getRedisKey(RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH));
		keys.add(RedisKeyBuild.getRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH));
		keys.add(String.valueOf(programOrderCreateDto.getProgramId()));
		data[0] = JSON.toJSONString(jsonArray);
		data[1] = JSON.toJSONString(addSeatDatajsonArray);
		ProgramCacheCreateOrderData programCacheCreateOrderData =
				programCacheCreateOrderResolutionOperate.programCacheOperate(keys, data);
		if (!Objects.equals(programCacheCreateOrderData.getCode(), BaseCode.SUCCESS.getCode())) {
			throw new DaMaiFrameException(Objects.requireNonNull(BaseCode.getRc(programCacheCreateOrderData.getCode())));
		}
		return programCacheCreateOrderData.getPurchaseSeatList();
	}

	private String doCreate(ProgramOrderCreateDto programOrderCreateDto, List<SeatVo> purchaseSeatList) {
		OrderCreateDto orderCreateDto = buildCreateOrderParam(programOrderCreateDto, purchaseSeatList);

		String orderNumber = createOrderByRpc(orderCreateDto, purchaseSeatList);

		DelayOrderCancelDto delayOrderCancelDto = new DelayOrderCancelDto();
		delayOrderCancelDto.setOrderNumber(orderCreateDto.getOrderNumber());
		delayOrderCancelSend.sendMessage(JSON.toJSONString(delayOrderCancelDto));

		return orderNumber;
	}

	private String doCreateV2(ProgramOrderCreateDto programOrderCreateDto, List<SeatVo> purchaseSeatList) {
		OrderCreateDto orderCreateDto = buildCreateOrderParam(programOrderCreateDto, purchaseSeatList);

		String orderNumber = createOrderByMq(orderCreateDto, purchaseSeatList);

		DelayOrderCancelDto delayOrderCancelDto = new DelayOrderCancelDto();
		delayOrderCancelDto.setOrderNumber(orderCreateDto.getOrderNumber());
		delayOrderCancelSend.sendMessage(JSON.toJSONString(delayOrderCancelDto));

		return orderNumber;
	}

	private OrderCreateDto buildCreateOrderParam(ProgramOrderCreateDto programOrderCreateDto, List<SeatVo> purchaseSeatList) {
		ProgramVo programVo = programService.simpleGetProgramAndShowMultipleCache(programOrderCreateDto.getProgramId());
		OrderCreateDto orderCreateDto = new OrderCreateDto();
		orderCreateDto.setOrderNumber(uidGenerator.getOrderNumber(programOrderCreateDto.getUserId(), ORDER_TABLE_COUNT));
		orderCreateDto.setProgramId(programOrderCreateDto.getProgramId());
		orderCreateDto.setProgramItemPicture(programVo.getItemPicture());
		orderCreateDto.setUserId(programOrderCreateDto.getUserId());
		orderCreateDto.setProgramTitle(programVo.getTitle());
		orderCreateDto.setProgramPlace(programVo.getPlace());
		orderCreateDto.setProgramShowTime(programVo.getShowTime());
		orderCreateDto.setProgramPermitChooseSeat(programVo.getPermitChooseSeat());
		BigDecimal databaseOrderPrice =
				purchaseSeatList.stream().map(SeatVo::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
		orderCreateDto.setOrderPrice(databaseOrderPrice);
		orderCreateDto.setCreateOrderTime(DateUtils.now());

		List<Long> ticketUserIdList = programOrderCreateDto.getTicketUserIdList();
		List<OrderTicketUserCreateDto> orderTicketUserCreateDtoList = new ArrayList<>();
		for (int i = 0; i < ticketUserIdList.size(); i++) {
			Long ticketUserId = ticketUserIdList.get(i);
			OrderTicketUserCreateDto orderTicketUserCreateDto = new OrderTicketUserCreateDto();
			orderTicketUserCreateDto.setOrderNumber(orderCreateDto.getOrderNumber());
			orderTicketUserCreateDto.setProgramId(programOrderCreateDto.getProgramId());
			orderTicketUserCreateDto.setUserId(programOrderCreateDto.getUserId());
			orderTicketUserCreateDto.setTicketUserId(ticketUserId);
			SeatVo seatVo =
					Optional.ofNullable(purchaseSeatList.get(i))
							.orElseThrow(() -> new DaMaiFrameException(BaseCode.SEAT_NOT_EXIST));
			orderTicketUserCreateDto.setSeatId(seatVo.getId());
			orderTicketUserCreateDto.setSeatInfo(seatVo.getRowCode() + "排" + seatVo.getColCode() + "列");
			orderTicketUserCreateDto.setTicketCategoryId(seatVo.getTicketCategoryId());
			orderTicketUserCreateDto.setOrderPrice(seatVo.getPrice());
			orderTicketUserCreateDto.setCreateOrderTime(DateUtils.now());
			orderTicketUserCreateDtoList.add(orderTicketUserCreateDto);
		}

		orderCreateDto.setOrderTicketUserCreateDtoList(orderTicketUserCreateDtoList);

		return orderCreateDto;
	}

	private String createOrderByRpc(OrderCreateDto orderCreateDto, List<SeatVo> purchaseSeatList) {
		ApiResponse<String> createOrderResponse = orderClient.create(orderCreateDto);
		if (!Objects.equals(createOrderResponse.getCode(), BaseCode.SUCCESS.getCode())) {
			log.error("创建订单失败 需人工处理 orderCreateDto : {}", JSON.toJSONString(orderCreateDto));
			updateProgramCacheDataResolution(orderCreateDto.getProgramId(), purchaseSeatList, OrderStatus.CANCEL);
			throw new DaMaiFrameException(createOrderResponse);
		}
		return createOrderResponse.getData();
	}

	private String createOrderByMq(OrderCreateDto orderCreateDto, List<SeatVo> purchaseSeatList) {
		CreateOrderMqDomain createOrderMqDomain = new CreateOrderMqDomain();
		CountDownLatch latch = new CountDownLatch(1);
		createOrderSend.sendMessage(JSON.toJSONString(orderCreateDto), sendResult -> {
			createOrderMqDomain.orderNumber = String.valueOf(orderCreateDto.getOrderNumber());
			assert sendResult != null;
			log.info("创建订单kafka发送消息成功 topic : {}", sendResult.getRecordMetadata().topic());
			latch.countDown();
		}, ex -> {
			log.error("创建订单kafka发送消息失败 error", ex);
			log.error("创建订单失败 需人工处理 orderCreateDto : {}", JSON.toJSONString(orderCreateDto));
			updateProgramCacheDataResolution(orderCreateDto.getProgramId(), purchaseSeatList, OrderStatus.CANCEL);
			createOrderMqDomain.daMaiFrameException = new DaMaiFrameException(ex);
			latch.countDown();
		});
		try {
			latch.await();
		}
		catch (InterruptedException e) {
			log.error("createOrderByMq InterruptedException", e);
			throw new DaMaiFrameException(e);
		}
		if (Objects.nonNull(createOrderMqDomain.daMaiFrameException)) {
			throw createOrderMqDomain.daMaiFrameException;
		}
		return createOrderMqDomain.orderNumber;
	}

	private void updateProgramCacheDataResolution(Long programId, List<SeatVo> seatVoList, OrderStatus orderStatus) {
		//如果要操作的订单状态不是未支付和取消，那么直接拒绝
		if (!(Objects.equals(orderStatus.getCode(), OrderStatus.NO_PAY.getCode()) ||
				Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode()))) {
			throw new DaMaiFrameException(BaseCode.OPERATE_ORDER_STATUS_NOT_PERMIT);
		}
		List<String> keys = new ArrayList<>();
		//这里key只是占位，并不起实际作用
		keys.add("#");

		String[] data = new String[3];
		Map<Long, Long> ticketCategoryCountMap =
				seatVoList.stream().collect(Collectors.groupingBy(SeatVo::getTicketCategoryId, Collectors.counting()));
		//更新票档数据集合
		JSONArray jsonArray = new JSONArray();
		ticketCategoryCountMap.forEach((k, v) -> {
			//这里是计算更新票档数据
			JSONObject jsonObject = new JSONObject();
			//票档数量的key
			jsonObject.put("programTicketRemainNumberHashKey", RedisKeyBuild.createRedisKey(
					RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, k).getRelKey());
			//票档id
			jsonObject.put("ticketCategoryId", String.valueOf(k));
			//如果是生成订单操作，则将扣减余票数量
			if (Objects.equals(orderStatus.getCode(), OrderStatus.NO_PAY.getCode())) {
				jsonObject.put("count", "-" + v);
				//如果是取消订单操作，则将恢复余票数量
			}
			else if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
				jsonObject.put("count", v);
			}
			jsonArray.add(jsonObject);
		});
		//座位map key:票档id  value:座位集合
		Map<Long, List<SeatVo>> seatVoMap =
				seatVoList.stream().collect(Collectors.groupingBy(SeatVo::getTicketCategoryId));
		JSONArray delSeatIdjsonArray = new JSONArray();
		JSONArray addSeatDatajsonArray = new JSONArray();
		seatVoMap.forEach((k, v) -> {
			JSONObject delSeatIdjsonObject = new JSONObject();
			JSONObject seatDatajsonObject = new JSONObject();
			String seatHashKeyDel = "";
			String seatHashKeyAdd = "";
			//如果是生成订单操作，则将座位修改为锁定状态
			if (Objects.equals(orderStatus.getCode(), OrderStatus.NO_PAY.getCode())) {
				//没有售卖座位的key
				seatHashKeyDel = (RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, k).getRelKey());
				//锁定座位的key
				seatHashKeyAdd = (RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, k).getRelKey());
				for (SeatVo seatVo : v) {
					seatVo.setSellStatus(SellStatus.LOCK.getCode());
				}
				//如果是取消订单操作，则将座位修改为未售卖状态
			}
			else if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
				//锁定座位的key
				seatHashKeyDel = (RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, k).getRelKey());
				//没有售卖座位的key
				seatHashKeyAdd = (RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, k).getRelKey());
				for (SeatVo seatVo : v) {
					seatVo.setSellStatus(SellStatus.NO_SOLD.getCode());
				}
			}
			//要进行删除座位的key
			delSeatIdjsonObject.put("seatHashKeyDel", seatHashKeyDel);
			//如果是订单创建，那么就扣除未售卖的座位id
			//如果是订单取消，那么就扣除锁定的座位id
			delSeatIdjsonObject.put("seatIdList", v.stream().map(SeatVo::getId).map(String::valueOf).collect(Collectors.toList()));
			delSeatIdjsonArray.add(delSeatIdjsonObject);
			//要进行添加座位的key
			seatDatajsonObject.put("seatHashKeyAdd", seatHashKeyAdd);
			//如果是订单创建的操作，那么添加到锁定的座位数据
			//如果是订单订单的操作，那么添加到未售卖的座位数据
			List<String> seatDataList = new ArrayList<>();
			//循环座位
			for (SeatVo seatVo : v) {
				//选放入座位的id
				seatDataList.add(String.valueOf(seatVo.getId()));
				//接着放入座位对象
				seatDataList.add(JSON.toJSONString(seatVo));
			}
			//要进行添加座位的数据
			seatDatajsonObject.put("seatDataList", seatDataList);
			addSeatDatajsonArray.add(seatDatajsonObject);
		});

		//票档相关数据
		data[0] = JSON.toJSONString(jsonArray);
		//要进行删除座位的key
		data[1] = JSON.toJSONString(delSeatIdjsonArray);
		//要进行添加座位的相关数据
		data[2] = JSON.toJSONString(addSeatDatajsonArray);
		//执行lua脚本
		programCacheResolutionOperate.programCacheOperate(keys, data);
	}
}
