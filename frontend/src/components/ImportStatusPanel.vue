<template>
  <section class="panel import-status" aria-labelledby="import-status-title">
    <div class="import-status-heading">
      <v-icon color="primary">mdi-database-clock-outline</v-icon>
      <h2 id="import-status-title">Carga histórica ainda não validada</h2>
    </div>
    <p>Os dados disponíveis podem ser consultados. Edição e cancelamento aguardam a validação inicial.</p>

    <p v-if="!job && !loading && !error" class="import-note">Nenhuma carga registrada. O acompanhamento começará após o envio do arquivo.</p>
    <v-progress-linear v-if="loading && !job" indeterminate color="primary" aria-label="Consultando carga" />
    <p v-if="error" class="field-error" role="alert">{{ error }}</p>

    <div v-if="job" class="import-details" aria-live="polite">
      <p><strong>Etapa atual:</strong> {{ phase }}</p>
      <v-progress-linear :indeterminate="progress === null" :value="progress || 0" color="primary" height="8" rounded />
      <p v-if="progress === null">Lotes preparados: {{ formatCount(job.preparedBatches) }}. O total ainda não é conhecido.</p>
      <p v-else>Processamento dos registros: {{ formatCount(job.completedBatches) }} de {{ formatCount(job.totalBatches) }} lotes ({{ progress }}%).</p>
      <p v-if="job.status === 'STAGING' && progress === 100" class="import-note">A validação final continua após o processamento dos lotes.</p>
      <div class="import-counts">
        <span>Itens publicados: <strong>{{ formatCount(job.publishedItems) }}</strong></span>
        <span>Pedidos publicados: <strong>{{ formatCount(job.publishedOrders) }}</strong></span>
        <span>Acompanhando há: <strong>{{ elapsed }}</strong></span>
      </div>
      <p v-if="job.status === 'COMPLETED_WITH_ISSUES'" class="import-note">
        A parte consistente da base está disponível; os registros com ocorrências continuam protegidos.
      </p>
      <p v-if="job.error" class="field-error" role="alert">{{ job.error }}</p>
    </div>
  </section>
</template>

<script lang="ts">
import Vue from 'vue'
import { errorMessage, request } from '../services/api'
import { batchPercent, formatCount, getActiveImport, importPhase, type ImportJob } from '../services/imports'

export default Vue.extend({
  data: () => ({
    job: null as ImportJob | null,
    loading: false,
    error: '',
    trackingSince: 0,
    now: Date.now(),
    refreshTimer: null as ReturnType<typeof setTimeout> | null,
    clockTimer: null as ReturnType<typeof setInterval> | null,
    pending: null as AbortController | null,
  }),
  computed: {
    phase(): string { return this.job ? importPhase(this.job) : '' },
    progress(): number | null { return this.job ? batchPercent(this.job) : null },
    elapsed(): string {
      const seconds = Math.max(0, Math.floor((this.now - this.trackingSince) / 1000))
      const hours = Math.floor(seconds / 3600)
      const minutes = Math.floor((seconds % 3600) / 60)
      const rest = seconds % 60
      return hours ? `${hours}h ${minutes}min ${rest}s` : `${minutes}min ${rest}s`
    },
  },
  mounted() {
    this.clockTimer = setInterval(() => { this.now = Date.now() }, 1000)
    this.load()
  },
  beforeDestroy() {
    if (this.refreshTimer) clearTimeout(this.refreshTimer)
    if (this.clockTimer) clearInterval(this.clockTimer)
    this.pending?.abort()
    this.pending = null
  },
  methods: {
    formatCount,
    async load() {
      const pending = new AbortController()
      this.pending = pending
      this.loading = true
      try {
        const job = await getActiveImport(pending.signal)
        if (this.pending !== pending) return
        if (job?.id !== this.job?.id) {
          this.trackingSince = job ? Date.now() : 0
          this.now = Date.now()
        }
        this.job = job
        this.error = ''
        if (job && (job.status === 'COMPLETED' || job.status === 'COMPLETED_WITH_ISSUES')) {
          // Uma leitura operacional confirma o estado atual antes de liberar ações na interface.
          await request('/orgaos', { params: { limite: 1 }, signal: pending.signal })
        }
      } catch (error) {
        if (!pending.signal.aborted) this.error = errorMessage(error)
      } finally {
        if (this.pending === pending) {
          this.pending = null
          this.loading = false
          this.refreshTimer = setTimeout(() => this.load(), 5000)
        }
      }
    },
  },
})
</script>
