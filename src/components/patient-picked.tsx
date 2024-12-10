import React from "react";

const PatientPicked = async () => {
  return (
    <div className="w-full h-full flex flex-col">
      <h3 className="p-2 font-bold">Patient</h3>
      <div className="text-xs text-gray-200">Active consultation</div>
      <div >TODO FNR</div>
      <div className="text-xs text-gray-200">Name</div>
      <div >TODO NAME</div>

      <button className="mt-3 p-2 bg-red-500 text-white rounded hover:bg-red-600">
        Close consultation
      </button>
    </div>
  );
};

export default PatientPicked;