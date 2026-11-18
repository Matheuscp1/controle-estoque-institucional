// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import CursorList from './CursorList.vue'

const flushPromises = () => new Promise<void>((resolve) => setTimeout(resolve, 0))

function mountList(fetchPage: (...args: unknown[]) => Promise<unknown[]>, filters = { descricao: '' }) {
  return mount(CursorList, {
    propsData: {
      title: 'Itens', emptyMessage: 'Nenhum item', filters, fetchPage, cursorKey: 'codigo', limit: 2,
    },
    stubs: ['v-btn', 'v-icon', 'v-alert', 'v-progress-linear'],
  })
}

describe('lista paginada', () => {
  it('usa o último código como cursor e evita avançar após página vazia', async () => {
    const fetchPage = vi.fn()
      .mockResolvedValueOnce([{ codigo: 'A' }, { codigo: 'B' }])
      .mockResolvedValueOnce([])
    const wrapper = mountList(fetchPage)
    await flushPromises()

    expect(wrapper.text()).toContain('Página 1')
    ;(wrapper.vm as any).advance()
    await flushPromises()

    expect(fetchPage.mock.calls[1][1]).toBe('B')
    expect((wrapper.vm as any).page).toBe(0)
    expect((wrapper.vm as any).canAdvance).toBe(false)
    wrapper.destroy()
  })

  it('descarta resposta antiga quando os filtros mudam', async () => {
    let completeOld!: (rows: unknown[]) => void
    const fetchPage = vi.fn()
      .mockImplementationOnce(() => new Promise<unknown[]>((resolve) => { completeOld = resolve }))
      .mockResolvedValueOnce([{ codigo: 'NOVO' }])
    const wrapper = mountList(fetchPage)
    await wrapper.setProps({ filters: { descricao: 'novo' } })
    await flushPromises()
    completeOld([{ codigo: 'ANTIGO' }])
    await flushPromises()

    expect((wrapper.vm as any).rows).toEqual([{ codigo: 'NOVO' }])
    expect(fetchPage.mock.calls[0][3].aborted).toBe(true)
    wrapper.destroy()
  })

  it('mostra erro e permite repetir a consulta', async () => {
    const fetchPage = vi.fn()
      .mockRejectedValueOnce(new Error('falhou'))
      .mockResolvedValueOnce([{ codigo: 'A' }])
    const wrapper = mountList(fetchPage)
    await flushPromises()
    expect((wrapper.vm as any).error).toBeTruthy()

    ;(wrapper.vm as any).retry()
    await flushPromises()
    expect((wrapper.vm as any).rows).toEqual([{ codigo: 'A' }])
    expect((wrapper.vm as any).error).toBe('')
    wrapper.destroy()
  })
})
