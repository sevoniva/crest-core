declare module 'sm-crypto' {
  export const sm2: {
    doEncrypt(message: string | number[], publicKey: string, cipherMode?: number): string
  }

  export const sm3: (
    input: string | number[],
    options?: {
      mode?: 'hmac'
      key?: string | number[]
    }
  ) => string

  export const sm4: {
    decrypt(
      ciphertext: string,
      key: string | number[],
      options?: {
        mode?: 'cbc'
        iv?: string | number[]
        padding?: 'pkcs#7' | 'pkcs#5' | 'none'
        output?: 'string' | 'array'
      }
    ): string
  }
}
