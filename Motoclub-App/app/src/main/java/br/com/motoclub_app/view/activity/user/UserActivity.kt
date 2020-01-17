package br.com.motoclub_app.view.activity.user

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.children
import br.com.motoclub_app.R
import br.com.motoclub_app.app.utils.DateUtils
import br.com.motoclub_app.app.utils.ImageUtils
import br.com.motoclub_app.model.User
import br.com.motoclub_app.repository.UserRepository
import br.com.motoclub_app.type.CargoType
import br.com.motoclub_app.view.activity.BaseActivity
import br.com.motoclub_app.view.activity.main.MainActivity
import br.com.motoclub_app.view.activity.user.contract.UserPresenter
import br.com.motoclub_app.view.activity.user.contract.UserView
import br.com.motoclub_app.view.fragment.BottomNavigationFragment
import com.afollestad.vvalidator.form
import com.afollestad.vvalidator.form.Form
import com.afollestad.vvalidator.form.FormResult
import com.bumptech.glide.Glide
import com.redmadrobot.inputmask.MaskedTextChangedListener
import kotlinx.android.synthetic.main.activity_user.*

class UserActivity : BaseActivity<UserPresenter>(), UserView {

    companion object {
        val TAG = UserActivity::class.java.simpleName
    }

    var isEdit = false
    var isReadOnly = false
    var imagePath: Uri? = null

    private lateinit var myForm: Form

    lateinit var user: User

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        initForm()
        initUser()
        initFormValidation()

        MaskedTextChangedListener.installOn(activity_user_telefone, "([00]) [00000]-[0000]")
        MaskedTextChangedListener.installOn(activity_user_data_nascimento, "[00]/[00]/[0000]")

        if (isReadOnly) {

            activity_user_add_photo_card.visibility = View.GONE
            activity_user_btn_salvar.visibility = View.GONE
            activity_user_password.visibility = View.GONE

            activity_user_form.children.forEach {

                if (it !is ImageView)
                    it.isEnabled = false
            }
        } else {
            activity_user_add_photo_card.setOnClickListener {

                val bottomNav = BottomNavigationFragment()

                bottomNav.show(supportFragmentManager, bottomNav.tag)

                bottomNav.onImageSelectedListener {

                    updateImage(it)
                    imagePath = it
                    bottomNav.dismiss()
                }
            }
        }

        activity_user_img.setOnClickListener {

            var image =
            if (imagePath != null) {
                imagePath.toString()
            } else {
                user.imageId
            }

            image?.let { ImageUtils.openImageViewer(this, it) }
        }

    }

    private fun salvar() {

        Log.i(TAG, "Validating form")
        val result = myForm.validate()

        if (result.success()) {
            Log.i(TAG, "Saving the User")
            user.apply {
                nome = activity_user_name.text.toString()
                apelido = activity_user_apelido.text.toString()
                tipoSanguineo = activity_user_tipo_sanguineo.selectedItem.toString()

                if (activity_user_data_nascimento.text.toString().isNotBlank())
                    nascimento = DateUtils.stringToCalendar(activity_user_data_nascimento.text.toString())

                cargo = CargoType.values()[activity_user_cargo.selectedItemPosition]
                telefone = activity_user_telefone.text.toString()
                email = activity_user_email.text.toString()
                password = activity_user_password.text.toString()

                imagePath?.apply {
                    imageId = this.toString()
                }
            }
            presenter.salvar(user)
        } else {

            Log.i(TAG, result.errors().toString())
        }
    }

    override fun onSalvar() {

        if (isEdit) {
            Log.i(TAG, "Is editting, so just back to who called this activity")
            super.onBackPressed()
        } else {
            Log.i(TAG, "Is a new user, so go to main activity and reset the activities stack")
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }
    }

    override fun showError(msg: String?) {
        Toast.makeText(this, msg ?: "Houve um erro ao salvar", Toast.LENGTH_LONG).show()
    }

    private fun initFormValidation() {

        myForm = form {
            inputLayout(R.id.input_layout_name) {
                isNotEmpty().description(getString(R.string.campo_obrigatorio))
            }

            inputLayout(R.id.input_layout_data_nascimento) {

                conditional({ activity_user_data_nascimento.text!!.isNotBlank() }) {
                    matches("^\\d{2}/\\d{2}/\\d{4}$").description(getString(R.string.invalid_date))
                }
            }

            inputLayout(R.id.input_layout_telefone) {

                conditional({ activity_user_telefone.text!!.isNotBlank() }) {
                    matches("""^(\(\d{2}\))? \d{5}-\d{4}$""").description(getString(R.string.invalid_phone))
                }
            }

            inputLayout(R.id.input_layout_email) {
                matches(Patterns.EMAIL_ADDRESS.toString()).description(getString(R.string.invalid_email))
            }

            inputLayout(R.id.input_layout_password) {
                isNotEmpty().description(getString(R.string.campo_obrigatorio))
            }
        }

        activity_user_btn_salvar.setOnClickListener { salvar() }
    }

    private fun initUser() {

        intent.extras?.apply {

            Log.i(TAG, "Have extras")
            if (this.containsKey("id")) {

                isEdit = true
                activity_user_email.isEnabled = false

                Log.i(TAG, "Have an ID Extra")
                val userId = getLong("id")

                user = if (UserRepository.loggedUser!!.id == userId) {
                    Log.i(TAG, "The ID is of the Logged User")
                    UserRepository.loggedUser!!
                } else {
                    Log.i(TAG, "Loading user from repository")
                    presenter.loadById(userId)
                }

                activity_user_name.setText(user.nome)
                activity_user_apelido.setText(user.apelido)
                activity_user_tipo_sanguineo.setSelection(
                    (activity_user_tipo_sanguineo.adapter as ArrayAdapter<String>).getPosition(
                        user.tipoSanguineo
                    )
                )
                activity_user_cargo.setSelection(CargoType.values().indexOf(user.cargo))
                activity_user_telefone.setText(user.telefone)
                activity_user_email.setText(user.email)
                activity_user_password.setText(user.password)

                user.nascimento?.apply { activity_user_data_nascimento.setText(DateUtils.calendarToString(this)) }

                user.imageId?.let {
                    updateImage(Uri.parse(it))
                }

            }

            if (this.containsKey("readOnly")) {
                isReadOnly = this.getBoolean("readOnly")
            }
        }

        if (!::user.isInitialized)
            user = User()
    }

    private fun initForm() {

        val cargosAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            CargoType.values().map { c -> c.description })

        cargosAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        activity_user_cargo.adapter = cargosAdapter
        activity_user_cargo.setSelection(CargoType.values().indexOf(CargoType.INT))

        val tipoSanguineoAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            arrayOf("A-", "A+", "B-", "B+", "AB-", "AB+", "O-", "O+")
        )

        tipoSanguineoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        activity_user_tipo_sanguineo.adapter = tipoSanguineoAdapter
    }

    private fun updateImage(uri: Uri?) {
        ImageUtils.loadImage(this, uri.toString(), activity_user_img)
    }
}