import { request } from './api'

export interface ImportJob {
  id: string
  status: 'PREPARING' | 'STAGING' | 'REVIEW_PENDING' | 'COMPLETED' | 'COMPLETED_WITH_ISSUES' | 'FAILED' | 'NEEDS_REVIEW'
  totalBatches: string
  completedBatches: string
  preparedBatches: string
  publishedItems: string
  publishedOrders: string
  issues: string
  error: string | null
}

export function importPhase(job: ImportJob): string {
  switch (job.status) {
    case 'PREPARING': return 'Lendo arquivo e preparando lotes'
    case 'STAGING': return Number(job.totalBatches) > 0 && Number(job.completedBatches) >= Number(job.totalBatches)
      ? 'Validando dados e finalizando publicação' : 'Processando registros'
    case 'REVIEW_PENDING': return 'Revalidando correções'
    case 'COMPLETED_WITH_ISSUES': return 'Concluída com pendências'
    case 'COMPLETED': return 'Concluída'
    case 'FAILED': return 'Falha na carga'
    case 'NEEDS_REVIEW': return 'Aguardando revisão'
  }
}

export function batchPercent(job: ImportJob): number | null {
  if (job.status === 'PREPARING' || Number(job.totalBatches) === 0) return null
  return Math.min(100, Math.round(100 * Number(job.completedBatches) / Number(job.totalBatches)))
}

export function formatCount(value: string): string {
  return BigInt(value).toLocaleString('pt-BR')
}

export function getActiveImport(signal?: AbortSignal): Promise<ImportJob | null> {
  return request<ImportJob | null>('/imports/active', { signal })
}
