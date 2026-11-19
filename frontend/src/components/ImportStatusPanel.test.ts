// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { importReadiness } from '../services/api'
import ImportStatusPanel from './ImportStatusPanel.vue'

const flushPromises = () => new Promise<void>((resolve) => setTimeout(resolve, 0))
const id = 'a'.repeat(64)
const stubs = ['v-icon', 'v-text-field', 'v-btn', 'v-progress-linear']

afterEach(() => {
  vi.unstubAllGlobals()
  importReadiness.ready = null
})

describe('painel da carga inicial', () => {
  it('mantém o aviso visível sem ID e valida antes de consultar', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(ImportStatusPanel, { stubs })

    expect(wrapper.text()).toContain('Edição e cancelamento aguardam a validação inicial')
    ;(wrapper.vm as any).jobIdInput = 'inválido'
    ;(wrapper.vm as any).track()
    await flushPromises()
    expect(fetchMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('ID de 64 caracteres')
    wrapper.destroy()
  })

  it('mostra a etapa, o progresso dos lotes e os dados publicados', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id, status: 'STAGING', preparedBatches: 4, completedBatches: 2,
      totalBatches: 4, publishedItems: 50000, publishedOrders: 1200, issues: 0, error: null,
    }), { headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(ImportStatusPanel, { stubs })
    ;(wrapper.vm as any).jobIdInput = id
    ;(wrapper.vm as any).track()
    await flushPromises()

    expect(fetchMock.mock.calls[0][0]).toBe(`/api/imports/${id}`)
    expect(wrapper.text()).toContain('Processando registros')
    expect(wrapper.text()).toContain('2 de 4 lotes (50%)')
    expect(wrapper.text()).toContain('50.000')
    expect(wrapper.text()).toContain('1.200')
    wrapper.destroy()
  })
})
