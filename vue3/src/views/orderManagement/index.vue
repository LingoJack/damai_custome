<template>
  <!--订单管理-->
  <Header></Header>
  <div class="red-line"></div>
  <div class="section">
    <MenuSideBar activeIndex="5" class="sidebarMenu"></MenuSideBar>
    <div class="right-section">
      <table>
        <thead>
        <tr>
          <th style="width: 390px">项目名称</th>
          <th style="width: 144px">票品张数</th>
          <th style="width: 130px">订单金额</th>
          <th style="width: 130px">交易状态</th>
          <th style="width: 210px">交易操作</th>
        </tr>
        </thead>
      </table>
      <div v-for="(order, index) in orderList" :key="index" class="orderBox">
        <div class="num">订单号: {{ order.orderNumber }}</div>
        <ul>
          <li>
            <img :src="order.programItemPicture" alt="">
            <div class="project">
              <div class="title">{{ order.programTitle }}</div>
              <div class="content">演出场次: {{ order.programShowTime }}</div>
              <div class="content">演出场馆: {{ order.programPlace }}</div>
            </div>
          </li>
          <li>{{ order.ticketCount }}</li>
          <li>
            <div class="price">￥ {{ order.orderPrice }}</div>
            <div class="money">(含运费￥0.00)</div>
          </li>
          <li>
            <div class="orderStatus">{{ getOrderStatus(order.orderStatus) }}</div>
            <router-link :to="{name:'orderDetail',params:{orderNumber:order.orderNumber}}" class=" link">
              订单详情
            </router-link>
          </li>
          <li>
            <button v-show="order.orderStatus == 1" class="orderDetial" @click="cancelOrder(order.orderNumber)">
              取消订单
            </button>
          </li>
        </ul>
      </div>

    </div>
  </div>
  <Footer class="foot"></Footer>


</template>

<script name="OrderManagement" setup>
import {onMounted, reactive, ref} from 'vue'
import MenuSideBar from '../../components/menuSidebar/index'
import Header from '../../components/header/index'
import Footer from '../../components/footer/index'
import {cancelOrderApi, getOrderListApi} from '@/api/order.js'
import {ElMessage} from "element-plus";
//获取用户信息
import useUserStore from "../../store/modules/user";

//订单列表数据
const orderList = ref(0)
const useUser = useUserStore()
//订单列表入参
const orderListParams = reactive({
  userId: useUser.userId
})

//订单列表方法
const getOrderList = () => {
  getOrderListApi(orderListParams).then(response => {
    orderList.value = response.data;
  })
}

function getOrderStatus(orderStatus) {
  if (orderStatus == 1) {
    return '未支付';
  }
  if (orderStatus == 2) {
    return '交易关闭';
  }
  if (orderStatus == 3) {
    return '已支付';
  }
  if (orderStatus == 4) {
    return '交易关闭';
  }
}

function cancelOrder(orderNumber) {
  const orderNumberParams = {orderNumber}
  cancelOrderApi(orderNumberParams).then(response => {
    if (response.code == '0') {
      ElMessage({
        message: '取消成功',
        type: 'success',
      })
    } else {
      ElMessage({
        message: response.message,
        type: 'error',
      })
    }
  })
}

onMounted(() => {
  getOrderList()
})
</script>


<style lang="scss" scoped>
.red-line {
  border-bottom: 5px solid rgba(255, 55, 29, 0.85);
}

.section {
  width: 1200px;
  margin: 15px auto 0;

  .sidebarMenu {
    //width: 201px;
    float: left;
  }

  .right-section {
    width: 950px;
    height: 646px;
    margin-left: 10px;
    float: right;
    overflow-y: scroll;

    table {
      width: 940px;
      -webkit-box-sizing: border-box;
      box-sizing: border-box;
      border-left: 1px solid transparent;
      border-right: 1px solid transparent;
      background: #f7f7f7;
      color: #333;
      padding: 12px 0 12px 20px;
      height: 40px;
      line-height: 16px;
      font-size: 12px;
      margin-bottom: 20px;


    }

    .orderBox {
      border: 1px solid #ebebeb;
      width: 100%;
      height: 150px;
      margin-bottom: 20px;

      .num {
        font-size: 12px;
        padding: 14px 0 14px 20px;
        background: #f7f7f7;
        color: #000;
        border-bottom: 1px solid #ebebeb;
      }

      ul {
        margin: 0;
        padding: 0;
        list-style: none;

        li {
          display: flex;
          flex-direction: row;
          float: left;
          font-size: 12px;
          background: #ffffff;
          height: 100px;
        }

        li:first-child {
          width: 390px;
          padding-left: 20px;
          padding-top: 13px;
          border-right: 1px solid #ebebeb;

          img {
            width: 62px;
            height: 80px;
            float: left;
          }

          .project {
            width: 293px;
            padding-left: 18px;

            .title {
              width: 210px;
              color: #4a4a4a;
              margin-bottom: 4px;
              display: inline-block;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;

            }

            .content {
              color: #9b9b9b;
              margin-bottom: 2px;
            }

          }

        }

        li:nth-child(2) {
          width: 100px;
          border-right: 1px solid #ebebeb;
          text-align: center;
          padding: 48px;
        }

        li:nth-child(3) {
          width: 133px;
          display: block;
          padding-top: 32px;
          border-right: 1px solid #ebebeb;
          text-align: center;

          .price {
            width: 100%;
          }

          .money {
            width: 100%;
          }
        }

        li:nth-child(4) {
          width: 133px;
          display: block;
          padding-top: 32px;
          border-right: 1px solid #ebebeb;
          text-align: center;

          .orderStatus {
            width: 100%;

          }

        }

        li:nth-child(5) {
          width: 168px;

          .orderDetial {
            display: block;
            width: 98px !important;
            height: 30px;
            line-height: 1;
            text-align: center;
            background-color: rgba(255, 55, 29, 0.85);
            color: #fff;
            font-size: 14px;
            border-radius: 20px;
            margin-top: 32px;
            border: none;
            margin-bottom: 10px;
            margin-left: 44px;

          }
        }
      }
    }


  }

}

.foot {

  margin-top: 676px;
}

:deep(.el-input__wrapper) {
  flex-grow: 0.3
}

.link {
  text-decoration: none; /* 去除下划线 */
}

</style>
