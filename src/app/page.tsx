import PatientPicker from "@/components/patient-picker";
import AppPicker from "@/components/app-picker";
import {Suspense} from "react";

export default function Home() {
  return (
    <div className="flex h-full">
      <div className="min-w-72 w-72 max-w-72 p-4 pt-0">
        <div id="patient-section" className="min-h-48 mt-2">
          <h3 className="p-2 font-bold">Your patients</h3>
          <Suspense fallback={<span className="loader"></span>}>
            <PatientPicker/>
          </Suspense>
        </div>
        <div className="mt-4">
          <h3 className="p-2 font-bold">SMART Apps</h3>
          <Suspense fallback={<span className="loader"></span>}>
            <AppPicker/>
          </Suspense>
        </div>
      </div>
      <div id="inner-frame"
           className="w-full h-full rounded-l-3xl bg-white text-black overflow-hidden">
        <div className="w-full h-full flex justify-center items-center">
          <div className="flex flex-col items-center">
            <img src="doctor.webp" alt="Doctor, doctoring at the doctors office" height="420"
                 width="420"/>
            <p className="font-bold">← Velg en pasient i pasientvelgeren</p>
          </div>
        </div>
      </div>
    </div>
  );
}
