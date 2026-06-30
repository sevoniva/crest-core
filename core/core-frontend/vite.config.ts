import common from './config/common';
import base from './config/base';
import lib from './config/lib';
import pages from './config/pages';
import { defineConfig, mergeConfig } from 'vite'

export default defineConfig(async ({mode}) => {
  if (mode === 'dev') {
    const { default: dev } = await import('./config/dev')
    return mergeConfig(common , mergeConfig(dev, pages))
  }

  if (mode === 'lib') {
    return mergeConfig(common , lib)
  }
  return mergeConfig(common, mergeConfig(base, pages))
})
