<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { decideApproval, listApprovals, submitApproval } from './approvals'
import type { Approval, ApprovalStatus, Decision } from './approvals'

const statusLabels: Record<ApprovalStatus, string> = {
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}

const approvals = ref<Approval[]>([])
const title = ref('')
const applicant = ref('')
const busy = ref(false)
const loaded = ref(false)
const error = ref('')

async function run(action: () => Promise<void>) {
  if (busy.value) return

  busy.value = true
  error.value = ''
  try {
    await action()
  } catch (cause) {
    error.value = cause instanceof Error && !(cause instanceof TypeError)
      ? cause.message
      : '请求失败，请确认 Java 服务已启动。'
  } finally {
    busy.value = false
  }
}

function refresh() {
  return run(async () => {
    approvals.value = (await listApprovals()).sort((a, b) => b.id - a.id)
    loaded.value = true
  })
}

function create() {
  return run(async () => {
    const approval = await submitApproval(title.value.trim(), applicant.value.trim())
    approvals.value = [approval, ...approvals.value]
    title.value = ''
    applicant.value = ''
  })
}

function decide(id: number, decision: Decision) {
  return run(async () => {
    const updated = await decideApproval(id, decision)
    approvals.value = approvals.value.map((approval) => approval.id === id ? updated : approval)
  })
}

onMounted(refresh)
</script>

<template>
  <main class="workspace">
    <header class="page-header">
      <h1>OA 审批示例</h1>
      <p>本地演示。服务重启后，申请记录会重置。</p>
    </header>

    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <div class="layout">
      <section class="panel" aria-labelledby="form-heading">
        <h2 id="form-heading">发起申请</h2>
        <form class="request-form" @submit.prevent="create">
          <label>
            申请事项
            <input v-model="title" maxlength="120" placeholder="例如 采购办公用品" :disabled="busy" required />
          </label>
          <label>
            申请人
            <input v-model="applicant" maxlength="40" placeholder="例如 李明" :disabled="busy" required />
          </label>
          <button
            class="primary"
            type="submit"
            :disabled="busy || !loaded || !title.trim() || !applicant.trim()"
          >
            提交申请
          </button>
        </form>
      </section>

      <section class="panel" aria-labelledby="list-heading" :aria-busy="busy">
        <div class="section-header">
          <h2 id="list-heading">申请记录</h2>
          <button type="button" :disabled="busy" @click="refresh">刷新</button>
        </div>
        <p v-if="!loaded && busy" class="empty" role="status">正在加载申请记录…</p>
        <p v-else-if="loaded && !approvals.length" class="empty">暂无申请记录。</p>
        <ul v-else-if="approvals.length" class="approval-list">
          <li v-for="approval in approvals" :key="approval.id" class="approval">
            <div class="approval-header">
              <div class="approval-detail">
                <h3>{{ approval.title }}</h3>
                <p>{{ approval.applicant }}</p>
              </div>
              <span class="status" :data-status="approval.status">{{ statusLabels[approval.status] }}</span>
            </div>
            <div v-if="approval.status === 'PENDING'" class="actions">
              <button type="button" :disabled="busy" @click="decide(approval.id, 'APPROVED')">通过</button>
              <button type="button" :disabled="busy" @click="decide(approval.id, 'REJECTED')">驳回</button>
            </div>
          </li>
        </ul>
      </section>
    </div>
  </main>
</template>

<style>
:root {
  font-family: Inter, system-ui, sans-serif;
  color: #1a2940;
  background: #f1f5fa;
}

* { box-sizing: border-box; }
body { margin: 0; }
h1, h2, h3, p { margin: 0; }
h1 { font-size: clamp(1.75rem, 4vw, 2.5rem); }
h2 { font-size: 1.125rem; }
h3 { font-size: 1rem; }

.workspace {
  max-width: 64rem;
  margin: 0 auto;
  padding: clamp(1rem, 5vw, 3rem);
}

.page-header { margin-bottom: 1.5rem; }
.page-header p { margin-top: .5rem; }
.page-header p, .empty, .approval-detail p { color: #526279; }
.error {
  margin-bottom: 1rem;
  padding: .75rem 1rem;
  background: #fce8ea;
  color: #8a2033;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.75fr);
  gap: 1rem;
}

.panel {
  align-self: start;
  min-width: 0;
  padding: 1.125rem;
  border: 1px solid #d8e1ec;
  border-radius: .75rem;
  background: #fff;
}

.request-form {
  display: grid;
  gap: .875rem;
  margin-top: 1rem;
}

.request-form label {
  display: grid;
  gap: .375rem;
  font-size: .875rem;
  font-weight: 600;
}

input, button {
  min-width: 0;
  padding: .625rem .75rem;
  border: 1px solid #b9c7d7;
  border-radius: .375rem;
  background: #fff;
  color: inherit;
  font: inherit;
}

input {
  width: 100%;
  font-size: 1rem;
}

button {
  cursor: pointer;
  font-size: .875rem;
  font-weight: 600;
}

button:hover:not(:disabled) { background: #eaf2ff; }
button:disabled {
  cursor: default;
  opacity: .55;
}

.primary {
  border-color: #1457b8;
  background: #1457b8;
  color: #fff;
}

.primary:hover:not(:disabled) { background: #0d458e; }

.section-header, .approval-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: .75rem;
}

.empty { margin-top: 1.125rem; }
.approval-list {
  margin: .75rem 0 0;
  padding: 0;
  list-style: none;
}

.approval {
  padding: .875rem 0;
  border-top: 1px solid #e0e7f1;
}

.approval:last-child { padding-bottom: 0; }
.approval-detail {
  min-width: 0;
  overflow-wrap: anywhere;
}

.approval-detail p {
  margin-top: .25rem;
  font-size: .875rem;
}

.status {
  flex: none;
  padding: .25rem .5rem;
  border-radius: .375rem;
  font-size: .8125rem;
}

.status[data-status='PENDING'] {
  color: #805100;
  background: #fff2d5;
}

.status[data-status='APPROVED'] {
  color: #1a6b44;
  background: #e2f5e9;
}

.status[data-status='REJECTED'] {
  color: #9a2544;
  background: #fce8ee;
}

.actions {
  display: flex;
  gap: .5rem;
  margin-top: .75rem;
}

@media (max-width: 42rem) {
  .layout { grid-template-columns: minmax(0, 1fr); }
}
</style>
