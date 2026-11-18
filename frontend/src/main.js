import Vue from 'vue'
import Vuetify from 'vuetify/lib/framework'
import VAlert from 'vuetify/lib/components/VAlert/VAlert'
import VApp from 'vuetify/lib/components/VApp/VApp'
import VAutocomplete from 'vuetify/lib/components/VAutocomplete/VAutocomplete'
import VBtn from 'vuetify/lib/components/VBtn/VBtn'
import { VCard, VCardActions, VCardText, VCardTitle } from 'vuetify/lib/components/VCard'
import VChip from 'vuetify/lib/components/VChip/VChip'
import VSimpleTable from 'vuetify/lib/components/VDataTable/VSimpleTable'
import VDialog from 'vuetify/lib/components/VDialog/VDialog'
import VSpacer from 'vuetify/lib/components/VGrid/VSpacer'
import VIcon from 'vuetify/lib/components/VIcon/VIcon'
import VMain from 'vuetify/lib/components/VMain/VMain'
import VProgressLinear from 'vuetify/lib/components/VProgressLinear/VProgressLinear'
import VSelect from 'vuetify/lib/components/VSelect/VSelect'
import VSnackbar from 'vuetify/lib/components/VSnackbar/VSnackbar'
import VSwitch from 'vuetify/lib/components/VSwitch/VSwitch'
import VTextarea from 'vuetify/lib/components/VTextarea/VTextarea'
import VTextField from 'vuetify/lib/components/VTextField/VTextField'
import pt from 'vuetify/src/locale/pt'
import '@mdi/font/css/materialdesignicons.css'
import 'vuetify/src/styles/main.sass'
import App from './App.vue'
import './styles.css'

Vue.use(Vuetify, {
  components: {
    VAlert, VApp, VAutocomplete, VBtn, VCard, VCardActions, VCardText, VCardTitle, VChip,
    VDialog, VIcon, VMain, VProgressLinear, VSelect, VSimpleTable, VSnackbar, VSpacer,
    VSwitch, VTextarea, VTextField,
  },
})

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
