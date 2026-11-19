import { describe, expect, it } from 'vitest'
import { batchPercent, importPhase, type ImportJob } from './imports'

const job = (status: ImportJob['status'], completedBatches = '0', totalBatches = '0'): ImportJob => ({
  id: 'abc', status, completedBatches, totalBatches, preparedBatches: '0',
  publishedItems: '0', publishedOrders: '0', issues: '0', error: null,
})

describe('progresso da carga', () => {
  it('mostra leitura do arquivo sem percentual antes de conhecer o total', () => {
    expect(importPhase(job('PREPARING'))).toBe('Lendo arquivo e preparando lotes')
    expect(batchPercent(job('PREPARING'))).toBeNull()
  })

  it('identifica processamento e validação sem tratar 100% dos lotes como fim', () => {
    expect(importPhase(job('STAGING', '2', '4'))).toBe('Processando registros')
    expect(batchPercent(job('STAGING', '2', '4'))).toBe(50)
    expect(importPhase(job('STAGING', '4', '4'))).toBe('Validando dados e finalizando publicação')
    expect(batchPercent(job('STAGING', '4', '4'))).toBe(100)
  })

  it('distingue conclusão com pendências de conclusão completa', () => {
    expect(importPhase(job('COMPLETED_WITH_ISSUES'))).toBe('Concluída com pendências')
    expect(importPhase(job('COMPLETED'))).toBe('Concluída')
  })
})
