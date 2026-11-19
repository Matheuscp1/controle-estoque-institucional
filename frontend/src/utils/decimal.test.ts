import { describe, expect, it } from 'vitest'
import { decimalInput, decimalMilliunits, formatDecimal } from './decimal'

describe('valores decimais', () => {
  it('aceita vírgula ou ponto e preserva três casas decimais', () => {
    expect(decimalInput(' 12,345 ')).toBe('12.345')
    expect(decimalInput('0.001')).toBe('0.001')
  })

  it.each(['-1', '1.2345', '1e3', '1.2.3', '12345678901234567', ''])('rejeita saldo inválido: %s', (value) => {
    expect(decimalInput(value)).toBeNull()
  })

  it('compara casas decimais sem arredondamento de ponto flutuante', () => {
    expect(decimalMilliunits('1.001')).toBe(1001n)
    expect(decimalMilliunits('1.01')).toBe(1010n)
    expect(decimalMilliunits('9999999999999999.999')).toBe(9999999999999999999n)
  })

  it('mostra saldo com separadores', () => {
    expect(formatDecimal('1234567.500')).toBe('1.234.567,5')
    expect(formatDecimal('0.000')).toBe('0')
  })
})
