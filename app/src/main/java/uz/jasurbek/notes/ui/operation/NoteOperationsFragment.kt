package uz.jasurbek.notes.ui.operation

import android.Manifest
import android.app.Activity.RESULT_CANCELED
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import coil.api.load
import dagger.android.support.DaggerAppCompatActivity
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_add_edit_note.*
import uz.jasurbek.notes.R
import uz.jasurbek.notes.data.Constants
import uz.jasurbek.notes.data.Constants.choosingPhotoOptions
import uz.jasurbek.notes.data.Constants.moreOptions
import uz.jasurbek.notes.data.Constants.noteStatusOptions
import uz.jasurbek.notes.data.model.Note
import uz.jasurbek.notes.extentions.showOptionsAlertDialog
import uz.jasurbek.notes.extentions.toast
import uz.jasurbek.notes.ui.list.LoadingNoteStatus
import uz.jasurbek.notes.util.Util
import java.io.IOException
import javax.inject.Inject


class NoteOperationsFragment : DaggerFragment() {

    @Inject
    lateinit var providerFactory: ViewModelProvider.Factory
    private val viewModel by viewModels<NoteOperationsViewModel> { providerFactory }
    private lateinit var currentNote: Note

    private var cameraImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_add_edit_note, container, false)


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initVariables()
        setupPageTitle()
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val isNewNote = arguments?.getString(Constants.BUNDLE_KEY_NOTE_OPERATION).isNullOrBlank()
        if (isNewNote) inflater.inflate(R.menu.save_not_menu, menu)
        else inflater.inflate(R.menu.edit_note_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if ((grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            toast("Permission denied")
            return
        }
        when (requestCode) {
            Constants.REQUEST_CODE_CHOOSE_GALLERY_IMAGE -> choosePhotoFromGallery()
            Constants.REQUEST_CODE_TAKE_PHOTO_IMAGE -> takePhotoFromCamera()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_CANCELED) return
        when (requestCode) {
            Constants.REQUEST_CODE_CHOOSE_GALLERY_IMAGE -> loadImage(data?.data)
            Constants.REQUEST_CODE_TAKE_PHOTO_IMAGE -> loadImage(cameraImageUri)
        }
    }

    private fun initVariables() {
        val currentNodeID = arguments?.getString(Constants.BUNDLE_KEY_NOTE_OPERATION)
        if (currentNodeID.isNullOrBlank()) {
            currentNote = Constants.rawNote
            setupUI()
        } else {
            viewModel.getNote(currentNodeID)
            observeNoteObject()
        }
    }

    private fun setupPageTitle() {
        val isNewNote = arguments?.getString(Constants.BUNDLE_KEY_NOTE_OPERATION).isNullOrBlank()
        val parentActivity = activity as? DaggerAppCompatActivity
        if (isNewNote) parentActivity?.supportActionBar?.title = "Add note"
        else parentActivity?.supportActionBar?.title = "Edit note"
    }

    private fun observeNoteObject() =
        viewModel.noteResponse.observe(viewLifecycleOwner, Observer {
            when (it) {
                is LoadingNoteStatus.OnLoading -> println("Loading")
                is LoadingNoteStatus.OnSuccess -> updateUI(it.notes[0])
                is LoadingNoteStatus.OnError -> toast(it.errorMessage)
            }
        })


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.saveEditNote -> toast("Edit saved")
            R.id.deleteNote -> toast("Note deleted")
            R.id.saveNote -> toast("Note added")
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateUI(note: Note) {
        currentNote = note
        setupUI()
    }

    private fun setupUI() {
        currentNote.imagePath?.let { loadImage(Uri.parse(it)) }
        loadStatus(Util.mapStatusToString(currentNote.status))
        loadTitle(currentNote.name)
        loadDescription(currentNote.description)
        setClickListeners()
    }

    private fun setClickListeners() {
        operationNoteImage.setOnClickListener { selectImage() }
        operationNoteStatus.setOnClickListener { selectStatus() }
        operationMoreOptionView.setOnClickListener { selectMoreOptions() }
    }

    private fun loadImage(imageUri: Uri?) = imageUri?.let {
        operationNoteImage.setPadding(0, 0, 0, 0)
        operationNoteImage.load(it)
    }

    private fun loadStatus(status: String?) {
        val result = "Status : ${status ?: noteStatusOptions.first()}"
        operationNoteStatus.text = result
    }

    private fun loadTitle(title: String?) = title?.let {
        operationNoteTitle.setText(it)
    }

    private fun loadDescription(desc: String?) = desc?.let {
        operationNoteDesc.setText(it)
    }


    private fun selectMoreOptions() =
        showOptionsAlertDialog(moreOptions, "More options") {
            when (it) {
                "Due date" -> toast(it)
                "Reminder" -> toast(it)
            }
        }


    private fun selectStatus() =
        showOptionsAlertDialog(noteStatusOptions, "Note status") {
            if (it == noteStatusOptions.last()) return@showOptionsAlertDialog
            currentNote.status = Util.mapStringToStatus(it)
            loadStatus(it)
        }


    private fun selectImage() =
        showOptionsAlertDialog(choosingPhotoOptions, "Choose your note image") {
            when (it) {
                "Take Photo" -> openCamera()
                "Choose from Gallery" -> chooseFromGallery()
            }
        }


    private fun openCamera() {
        if (hasPermissionGranted(Manifest.permission.CAMERA)) takePhotoFromCamera()
        else requestPermission(
            arrayOf(Manifest.permission.CAMERA),
            Constants.REQUEST_CODE_TAKE_PHOTO_IMAGE
        )
    }

    private fun chooseFromGallery() {
        if (hasPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE) &&
            hasPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        ) choosePhotoFromGallery()
        else requestPermission(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            Constants.REQUEST_CODE_CHOOSE_GALLERY_IMAGE
        )
    }


    private fun requestPermission(array: Array<String>, requestCode: Int) =
        ActivityCompat.requestPermissions(
            requireActivity(),
            array,
            requestCode
        )


    private fun hasPermissionGranted(permission: String) =
        ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED


    private fun choosePhotoFromGallery() = with(Intent()) {
        type = "image/*"
        action = Intent.ACTION_GET_CONTENT
        startActivityForResult(this, Constants.REQUEST_CODE_CHOOSE_GALLERY_IMAGE)
    }


    private fun takePhotoFromCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            try {
                val imageFile = viewModel.createImageFile(activity)
                cameraImageUri = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP)
                    FileProvider.getUriForFile(requireContext(), Constants.AUTHORITY, imageFile)
                else Uri.fromFile(imageFile)

                intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                startActivityForResult(
                    intent,
                    Constants.REQUEST_CODE_TAKE_PHOTO_IMAGE
                )
            } catch (e: IOException) {
                toast("Error while try to take photo")
            }
        }
    }


}