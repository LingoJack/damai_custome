<template>
  <Header></Header>
  <el-row>
    <el-form ref="authRef" :model="authForm" :rules="authRules" class="login-form">
      <el-col :span="24">
        <el-form-item label="请输入真实姓名:" prop="relName">
          <el-input
              v-model="authForm.relName"
              class="input-with-select"
              show-password
              type="password"
          ></el-input>
        </el-form-item>
        <el-form-item label="请输入身份证号码:" prop="idNumber">
          <el-input
              v-model="authForm.idNumber"
              class="input-with-select"
              show-password
              type="password"
          ></el-input>
        </el-form-item>
      </el-col>
      <el-button
          class="btn"
          size="large"
          type="primary"
          @click.prevent="savePsd"
      ><span>保存</span></el-button>
    </el-form>
  </el-row>
  <Footer class="foot"></Footer>
</template>

<script setup>

import Header from '../../../components/header/index'
import Footer from '../../../components/footer/index'
import {ElMessage} from "element-plus"
import {getUserIdKey} from "../../../utils/auth"
import {reactive, ref} from 'vue'
import {useRouter} from 'vue-router'
import useUserStore from '@/store/modules/user'
import {getAuthentication} from "../../../api/accountSettings";


const router = useRouter();
const userStore = useUserStore()
const authForm = ref({
  idNumber: '',
  relName: '',
  id: getUserIdKey()
})


const authRules = reactive({
      idNumber: [],
      relName: [],
    }
)


function savePsd() {
  getAuthentication(authForm.value).then(response => {
    if (response.code == '0') {
      ElMessage({
        message: '保存成功',
        type: 'success',
      })

      userStore.logOut().then(() => {
        location.href = '../../login';
      })

    } else {
      ElMessage({
        message: response.message,
        type: 'error',
      })
    }
  })
}
</script>

<style lang="scss" scoped>
.el-row {
  width: 400px;
  height: 400px;
  margin: 100px auto 30px;
}

.btn {
  margin-left: 130px;
  background: rgba(255, 55, 29, 0.85);
  border: none;
}
</style>
