const decimalPattern = /^\d{1,16}(?:\.\d{1,3})?$/

export function decimalInput(value) {
  const normalized = String(value ?? '').trim().replace(',', '.')
  return decimalPattern.test(normalized) ? normalized : null
}

export function decimalMilliunits(value) {
  const [whole, fraction = ''] = value.split('.')
  return BigInt(whole + fraction.padEnd(3, '0'))
}

export function formatDecimal(value) {
  const [whole, fraction = ''] = String(value).split('.')
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
  const decimals = fraction.replace(/0+$/, '')
  return decimals ? `${grouped},${decimals}` : grouped
}
