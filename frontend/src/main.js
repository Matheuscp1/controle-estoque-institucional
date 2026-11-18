import Vue from 'vue'
import Vuetify from 'vuetify'
import pt from 'vuetify/src/locale/pt'
import '@mdi/font/css/materialdesignicons.css'
import 'vuetify/dist/vuetify.min.css'
import App from './App.vue'
import './styles.css'

Vue.use(Vuetify)

new Vue({
  vuetify: new Vuetify({
    lang: { locales: { pt }, current: 'pt' },
    theme: {
      themes: {
        light: { primary: '#205b45', error: '#a1353f', success: '#205b45' },
      },
    },
  }),
  render: (createElement) => createElement(App),
}).$mount('#app')
