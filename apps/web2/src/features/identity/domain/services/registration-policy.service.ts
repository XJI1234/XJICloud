export function isRegistrationPhone(phone: string): boolean {
  return /^1[3-9][0-9]{9}$/.test(phone)
}

export function isRegistrationPassword(password: string): boolean {
  const bytes = Array.from(password).reduce((total, char) => {
    const code = char.codePointAt(0)!
    return total + (code <= 0x7f ? 1 : code <= 0x7ff ? 2 : code <= 0xffff ? 3 : 4)
  }, 0)
  return password.length >= 8 && password.length <= 64 && bytes <= 72
}
