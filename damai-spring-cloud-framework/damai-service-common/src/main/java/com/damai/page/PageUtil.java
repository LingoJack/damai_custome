package com.damai.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.damai.dto.BasePageDto;
import com.github.pagehelper.PageInfo;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页工具类，提供分页参数的解析和转换功能
 **/
public class PageUtil {

    /**
     * 根据基础分页请求DTO获取分页参数对象
     *
     * @param basePageDto 基础分页请求DTO，包含页码和页面大小信息
     * @param <T>         泛型参数，表示页面数据的类型
     * @return 分页参数对象
     */
    public static <T> IPage<T> getPageParams(BasePageDto basePageDto) {
        return getPageParams(basePageDto.getPageNumber(), basePageDto.getPageSize());
    }

    /**
     * 根据页码和页面大小获取分页参数对象
     *
     * @param pageNumber 页码，表示当前是第几页
     * @param pageSize   页面大小，表示每页包含多少条记录
     * @param <T>        泛型参数，表示页面数据的类型
     * @return 分页参数对象
     */
    public static <T> IPage<T> getPageParams(int pageNumber, int pageSize) {
        return new Page<>(pageNumber, pageSize);
    }

    /**
     * 将PageInfo对象转换为自定义PageVo对象，同时转换页面中的数据类型
     *
     * @param pageInfo PageInfo对象，包含分页信息和数据列表
     * @param function 函数式接口，用于转换列表中的每个元素
     * @param <OLD>    泛型参数，表示原始页面数据的类型
     * @param <NEW>    泛型参数，表示转换后的页面数据的类型
     * @return 自定义的PageVo对象
     */
    public static <OLD, NEW> PageVo<NEW> convertPage(PageInfo<OLD> pageInfo, Function<? super OLD, ? extends NEW> function) {
        return new PageVo<>(pageInfo.getPageNum(),
                pageInfo.getPageSize(),
                pageInfo.getTotal(),
                pageInfo.getList().stream().map(function).collect(Collectors.toList()));
    }

    /**
     * 将MyBatis-Plus的IPage对象转换为自定义PageVo对象，同时转换页面中的数据类型
     *
     * @param iPage    IPage对象，包含分页信息和数据列表
     * @param function 函数式接口，用于转换列表中的每个元素
     * @param <OLD>    泛型参数，表示原始页面数据的类型
     * @param <NEW>    泛型参数，表示转换后的页面数据的类型
     * @return 自定义的PageVo对象
     */
    public static <OLD, NEW> PageVo<NEW> convertPage(IPage<OLD> iPage, Function<? super OLD, ? extends NEW> function) {
        return new PageVo<>(iPage.getCurrent(),
                iPage.getSize(),
                iPage.getTotal(),
                iPage.getRecords().stream().map(function).collect(Collectors.toList()));
    }
}
