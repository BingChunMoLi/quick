<template>
  <div class="about">
    <h1>This is an about page</h1>
    <el-button @click="get">get根目录</el-button>
    <el-button @click="hello">简单类型get</el-button>
    <el-button @click="postHello">简单对象psot</el-button>
    <el-button @click="postHello2">复杂对象Post</el-button>
  </div>
</template>

<script lang="ts" setup>
import request from '@/util/request'

function get() {
  request.get('/', {})
}
function hello() {
  request.get("/hello", {str: 'world'})
}

function postHello() {
  request.post("/hello2", {str: 'world'})
}

function postHello2() {
  request.post("/hello2", {
    str: 'world',
    a: {
      b: 'c',
      c: 'd'
    }})
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
