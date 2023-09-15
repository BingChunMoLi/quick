<template>
  <div class="about">
    <h1>This is an about page</h1>
    <el-button @click="gen">gen</el-button>
    <el-button @click="hello">hello</el-button>
  </div>
</template>

<script lang="ts" setup>
import request from '@/util/request'

import {sign} from "@/util/sign"

console.log(sign("333"))
function gen() {
  request.get('/', {})
}
function hello() {
  request.get("/hello", {str: 'world'})
}
</script>

<style>
@media (min-width: 1024px) {
  .about {
    min-height: 100vh;
    display: flex;
    align-items: center;
  }
}
</style>
