const decimalPattern = /^\d{1,16}(?:\.\d{1,3})?$/

export function decimalInput(value: string | number): string | null {
  const normalized = String(value ?? '').trim().replace(',', '.')
  return decimalPattern.test(normalized) ? normalized : null
}

export function decimalMilliunits(value: string): bigint {
  const [whole, fraction = ''] = value.split('.')
  return BigInt(whole + fraction.padEnd(3, '0'))
}

export function formatDecimal(value: string | number): string {
  const [whole, fraction = ''] = String(value).split('.')
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
  const decimals = fraction.replace(/0+$/, '')
  return decimals ? `${grouped},${decimals}` : grouped
}
